package io.qwenbridge.core.expansion;

import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.extraction.EntityDictionary;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.spelling.ProtectedTokens;
import io.qwenbridge.core.translation.PreservationIssue;
import io.qwenbridge.core.translation.TranslationOutcome;
import io.qwenbridge.core.translation.TranslationProvider;
import io.qwenbridge.core.translation.TranslationResult;
import io.qwenbridge.core.translation.TranslationResult.Failure;
import io.qwenbridge.core.translation.TranslationStage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/**
 * Universal multilingual query expansion (QB-286): one query becomes one result per configured
 * target language. The engine is stateless and holds no language list; providers declare what they
 * can translate and the engine selects them by capability.
 *
 * <p>For each distinct, valid target, in configured order:
 *
 * <ol>
 *   <li>Same language as the (reliably detected) source: {@code UNCHANGED}, the source text is the
 *       variant; no provider is called.
 *   <li>Otherwise the providers are filtered: none at all is {@code PROVIDER_UNAVAILABLE}; none
 *       producing the target is {@code UNSUPPORTED_LANGUAGE}; none serving this source and target
 *       is {@code UNSUPPORTED_LANGUAGE_PAIR} (or {@code SOURCE_UNDETERMINED} when the source is
 *       unknown and nothing detects it); none ready is {@code PROVIDER_UNAVAILABLE} with the
 *       provider's reason.
 *   <li>Ready providers are tried in configured order through the {@link TranslationStage}, which
 *       enforces the time budget, rejects implausible output and rejects translations that change
 *       numbers, units, currencies, operators, negations, entities or identifiers ({@link
 *       TranslationStage.Policy#ENFORCE}). The engine also requires every protected identifier
 *       ({@link ProtectedTokens#verbatim}) to survive verbatim. A failed attempt falls through to
 *       the next ready provider while time is left.
 *   <li>A translation whose reported score, or coverage metadata, is below the minimum is {@code
 *       LOW_CONFIDENCE}.
 * </ol>
 *
 * <p>Targets run in parallel up to {@link ExpansionLimits#maxParallel()}, and each also takes a
 * permit from an optional process-wide semaphore, so the total number of concurrent translations is
 * bounded. The overall deadline and the cancellation signal stop waiting; unfinished targets are
 * reported, never dropped, and their workers are interrupted.
 */
public final class QueryExpander {

  /** Provider metadata key: share of translatable words covered, {@code 0..1}. */
  public static final String COVERAGE = "coverage";

  private static final Duration POLL = Duration.ofMillis(20);

  private QueryExpander() {}

  /**
   * @param original the query exactly as received
   * @param sourceText the text to expand (normalized, typo-corrected when applied)
   */
  public record Request(
      String original,
      String sourceText,
      SourceLanguage source,
      ExpansionSettings settings,
      EntityDictionary dictionary) {
    public Request {
      Objects.requireNonNull(original, "original must not be null");
      Objects.requireNonNull(sourceText, "sourceText must not be null");
      Objects.requireNonNull(source, "source must not be null");
      Objects.requireNonNull(settings, "settings must not be null");
      dictionary = dictionary == null ? EntityDictionary.EMPTY : dictionary;
    }
  }

  public static ExpansionResult expand(
      Request request,
      List<TranslationProvider> providers,
      ExpansionLimits limits,
      Optional<Semaphore> shared,
      BooleanSupplier cancelled) {
    long started = System.nanoTime();
    ExpansionSettings settings = request.settings();
    String ref = reference(request.original());
    List<TargetLanguage> configured = settings.resolved();
    if (!settings.enabled())
      return result(
          ExpansionResult.Status.DISABLED,
          request,
          ref,
          List.of(),
          List.of(),
          started,
          false,
          List.of());
    if (configured.isEmpty())
      return result(
          ExpansionResult.Status.NOT_CONFIGURED,
          request,
          ref,
          configured,
          List.of(),
          started,
          false,
          List.of());

    List<String> explanations = new ArrayList<>();
    final Request resolved = withSource(request, resolveSource(request, providers, explanations));
    List<TargetLanguage> reported = configured.stream().filter(TargetLanguage::reported).toList();
    TargetResult[] results = new TargetResult[reported.size()];
    long deadline = started + limits.overall().toNanos();
    Semaphore local = new Semaphore(limits.maxParallel());
    List<Future<TargetResult>> futures = new ArrayList<>();
    boolean wasCancelled = false;
    ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    try {
      for (int i = 0; i < reported.size(); i++) {
        TargetLanguage entry = reported.get(i);
        if (!entry.usable()) {
          results[i] = invalid(entry, ref);
          futures.add(null);
          continue;
        }
        futures.add(
            workers.submit(
                () ->
                    target(
                        entry, resolved, ref, providers, limits, deadline, local, shared,
                        cancelled)));
      }
      for (int i = 0; i < futures.size(); i++) {
        Future<TargetResult> f = futures.get(i);
        if (f == null) continue;
        results[i] = await(f, deadline, cancelled);
        if (results[i] == null) {
          wasCancelled = cancelled.getAsBoolean();
          f.cancel(true);
          results[i] =
              failed(reported.get(i), resolved, ref, wasCancelled ? "CANCELLED" : "TIMEOUT", 0);
        }
      }
    } finally {
      workers.shutdownNow();
    }

    List<TargetResult> list = List.of(results);
    ExpansionResult.Status status;
    if (wasCancelled || cancelled.getAsBoolean()) status = ExpansionResult.Status.CANCELLED;
    else if (list.stream().allMatch(r -> r.status().succeeded()))
      status = ExpansionResult.Status.COMPLETE;
    else if (settings.policy() == ExpansionPolicy.STRICT_ALL_TARGETS)
      status = ExpansionResult.Status.FAILED;
    else if (list.stream().anyMatch(r -> r.status().usable(settings.policy())))
      status = ExpansionResult.Status.PARTIAL;
    else status = ExpansionResult.Status.FAILED;
    boolean useVariants =
        status == ExpansionResult.Status.COMPLETE || status == ExpansionResult.Status.PARTIAL;
    return result(status, resolved, ref, configured, list, started, useVariants, explanations);
  }

  /** Share of known words from which provider vocabulary establishes the source language. */
  static final double VOCABULARY_EVIDENCE = 0.5;

  /**
   * The source language used for expansion. Mixed, declared, and deterministic or heuristic
   * detections (script-based) are kept. A statistical detection without a score, or no detection,
   * is checked against the vocabulary of the ready providers: when one language covers at least
   * half of the plain words and more than the detected language does, it becomes the source, and
   * the decision is explained. Without evidence the detection stands.
   */
  static SourceLanguage resolveSource(
      Request request, List<TranslationProvider> providers, List<String> explanations) {
    SourceLanguage s = request.source();
    if (s.mixed() || s.basis() == SourceLanguage.Basis.DECLARED) return s;
    Confidence.Kind kind = s.confidence().kind();
    boolean statistical =
        s.basis() == SourceLanguage.Basis.UNDETERMINED || kind == Confidence.Kind.MODEL_DECISION;
    if (!statistical) return s;
    java.util.Map<String, Double> evidence = new java.util.TreeMap<>();
    for (TranslationProvider p : providers) {
      if (!p.availability().isReady()) continue;
      try {
        p.vocabularyEvidence(request.sourceText())
            .forEach((l, v) -> evidence.merge(l, v, Math::max));
      } catch (RuntimeException e) {
        // A provider's evidence is optional; a failure leaves the detection as it was.
      }
    }
    String detected = s.tag().isUndetermined() ? null : s.tag().primaryLanguage();
    String best = null;
    double bestShare = 0;
    for (var e : evidence.entrySet())
      if (e.getValue() > bestShare) {
        best = e.getKey();
        bestShare = e.getValue();
      }
    if (best == null || bestShare < VOCABULARY_EVIDENCE) return s;
    double detectedShare = detected == null ? 0 : evidence.getOrDefault(detected, 0.0);
    Optional<LanguageTag> tag = LanguageTag.parse(best);
    if (tag.isEmpty() || best.equals(detected) || detectedShare >= bestShare) return s;
    explanations.add(
        String.format(
            Locale.ROOT,
            "source language %s from provider vocabulary (%.0f%% of words known); the detector %s",
            best,
            bestShare * 100,
            detected == null ? "was inconclusive" : "said " + detected + " without a score"));
    return SourceLanguage.vocabulary(tag.get());
  }

  private static Request withSource(Request r, SourceLanguage source) {
    return source == r.source()
        ? r
        : new Request(r.original(), r.sourceText(), source, r.settings(), r.dictionary());
  }

  private static TargetResult await(
      Future<TargetResult> f, long deadline, BooleanSupplier cancelled) {
    while (true) {
      if (cancelled.getAsBoolean()) return null;
      long remaining = deadline - System.nanoTime();
      if (remaining <= 0) return null;
      try {
        return f.get(Math.min(remaining, POLL.toNanos()), TimeUnit.NANOSECONDS);
      } catch (TimeoutException e) {
        // poll again: deadline and cancellation are checked between waits
      } catch (ExecutionException e) {
        return null;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return null;
      }
    }
  }

  private static TargetResult target(
      TargetLanguage entry,
      Request request,
      String ref,
      List<TranslationProvider> providers,
      ExpansionLimits limits,
      long deadline,
      Semaphore local,
      Optional<Semaphore> shared,
      BooleanSupplier cancelled) {
    long started = System.nanoTime();
    LanguageTag target = entry.language().orElseThrow();
    LanguageTag source = request.source().tag();
    Optional<LanguageTag> sourceLanguage =
        source.isUndetermined() ? Optional.empty() : Optional.of(source);

    if (sameLanguage(source, target))
      return new TargetResult(
          entry.configured(),
          Optional.of(target),
          sourceLanguage,
          ref,
          TargetStatus.UNCHANGED,
          Optional.of(request.sourceText()),
          Optional.empty(),
          Confidence.deterministic(),
          Optional.empty(),
          Optional.empty(),
          true,
          false,
          millis(started));
    if (providers.isEmpty())
      return problem(
          entry, sourceLanguage, ref, TargetStatus.PROVIDER_UNAVAILABLE, "NO_PROVIDER", started);
    List<TranslationProvider> producing =
        providers.stream().filter(p -> p.capabilities().supportsTarget(target)).toList();
    if (producing.isEmpty())
      return problem(
          entry,
          sourceLanguage,
          ref,
          TargetStatus.UNSUPPORTED_LANGUAGE,
          "no installed provider produces " + target.value(),
          started);
    List<TranslationProvider> capable =
        producing.stream().filter(p -> p.capabilities().supportsPair(source, target)).toList();
    if (capable.isEmpty()) {
      if (source.isUndetermined())
        return problem(
            entry,
            sourceLanguage,
            ref,
            TargetStatus.SOURCE_UNDETERMINED,
            "source language undetermined; no provider detects it",
            started);
      return problem(
          entry,
          sourceLanguage,
          ref,
          TargetStatus.UNSUPPORTED_LANGUAGE_PAIR,
          (request.source().mixed() ? "mixed-language source; " : "")
              + "no provider translates "
              + source.value()
              + " to "
              + target.value(),
          started);
    }
    List<TranslationProvider> ready =
        capable.stream().filter(p -> p.availability().isReady()).toList();
    if (ready.isEmpty()) {
      TranslationProvider first = capable.getFirst();
      return problem(
          entry,
          sourceLanguage,
          ref,
          TargetStatus.PROVIDER_UNAVAILABLE,
          first.id()
              + ": "
              + first.availability().state().name()
              + " ("
              + first.availability().detail()
              + ")",
          started);
    }

    boolean localPermit = false;
    boolean sharedPermit = false;
    try {
      localPermit = acquire(local, deadline);
      if (!localPermit) return failed(entry, request, ref, "TIMEOUT", started);
      if (shared.isPresent()) {
        sharedPermit = acquire(shared.get(), deadline);
        if (!sharedPermit) return failed(entry, request, ref, "BUSY", started);
      }
      TargetResult last = null;
      for (TranslationProvider provider : ready) {
        if (cancelled.getAsBoolean()) return failed(entry, request, ref, "CANCELLED", started);
        long remaining = deadline - System.nanoTime();
        if (remaining <= Duration.ofMillis(1).toNanos())
          return last != null ? last : failed(entry, request, ref, "TIMEOUT", started);
        Duration budget = min(limits.perLanguage(), Duration.ofNanos(remaining));
        last = attempt(entry, request, ref, provider, target, source, budget, started);
        if (last.status() == TargetStatus.SUCCESS || last.status() == TargetStatus.LOW_CONFIDENCE)
          return last;
      }
      return last;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return failed(entry, request, ref, "CANCELLED", started);
    } finally {
      if (sharedPermit) shared.get().release();
      if (localPermit) local.release();
    }
  }

  private static TargetResult attempt(
      TargetLanguage entry,
      Request request,
      String ref,
      TranslationProvider provider,
      LanguageTag target,
      LanguageTag source,
      Duration budget,
      long started) {
    TranslationOutcome outcome =
        new TranslationStage(Optional.of(provider), budget)
            .translate(
                request.sourceText(),
                source,
                Optional.of(target),
                request.dictionary(),
                TranslationStage.Policy.ENFORCE);
    Optional<LanguageTag> sourceLanguage =
        source.isUndetermined() ? Optional.empty() : Optional.of(source);
    TranslationResult result = outcome.result();
    if (result instanceof TranslationResult.Translated t) {
      List<String> missing = new ArrayList<>();
      for (String token : ProtectedTokens.verbatim(request.sourceText()))
        if (!t.text().contains(token)) missing.add(token);
      Optional<LanguageTag> detected = Optional.of(t.source());
      if (!missing.isEmpty())
        return new TargetResult(
            entry.configured(),
            Optional.of(target),
            detected,
            ref,
            TargetStatus.FAILED,
            Optional.empty(),
            Optional.of(provider.id()),
            t.confidence(),
            Optional.of("NOT_PRESERVED: " + missing.size() + " protected identifier(s) changed"),
            outcome.preservation(),
            false,
            false,
            millis(started));
      Optional<String> low = lowConfidence(t, request.settings().minConfidence());
      return new TargetResult(
          entry.configured(),
          Optional.of(target),
          detected,
          ref,
          low.isPresent() ? TargetStatus.LOW_CONFIDENCE : TargetStatus.SUCCESS,
          Optional.of(t.text()),
          Optional.of(provider.id()),
          t.confidence(),
          low,
          outcome.preservation(),
          true,
          false,
          millis(started));
    }
    if (result instanceof TranslationResult.Failed f) {
      TargetStatus status =
          switch (f.failure()) {
            case NO_PROVIDER -> TargetStatus.PROVIDER_UNAVAILABLE;
            case UNSUPPORTED_LANGUAGE_PAIR -> TargetStatus.UNSUPPORTED_LANGUAGE_PAIR;
            case UNDETERMINED_SOURCE -> TargetStatus.SOURCE_UNDETERMINED;
            default -> TargetStatus.FAILED;
          };
      return new TargetResult(
          entry.configured(),
          Optional.of(target),
          sourceLanguage,
          ref,
          status,
          Optional.empty(),
          Optional.of(provider.id()),
          Confidence.unknown(),
          Optional.of(reason(f.failure(), outcome)),
          outcome.preservation(),
          outcome.preservation().stream()
              .flatMap(p -> p.issues().stream())
              .noneMatch(i -> i.kind() == PreservationIssue.Kind.IDENTIFIER),
          false,
          millis(started));
    }
    // A skip cannot happen here (same language is decided before); report it honestly.
    return new TargetResult(
        entry.configured(),
        Optional.of(target),
        sourceLanguage,
        ref,
        TargetStatus.FAILED,
        Optional.empty(),
        Optional.of(provider.id()),
        Confidence.unknown(),
        Optional.of("SKIPPED"),
        Optional.empty(),
        true,
        false,
        millis(started));
  }

  private static String reason(Failure failure, TranslationOutcome outcome) {
    if (failure == Failure.NOT_PRESERVED && outcome.preservation().isPresent()) {
      Set<String> kinds = new LinkedHashSet<>();
      outcome.preservation().get().issues().forEach(i -> kinds.add(i.kind() + " " + i.direction()));
      return "NOT_PRESERVED: " + String.join(", ", kinds);
    }
    return failure.name();
  }

  private static Optional<String> lowConfidence(TranslationResult.Translated t, double minimum) {
    if (t.confidence().value().isPresent() && t.confidence().value().getAsDouble() < minimum)
      return Optional.of("score " + t.confidence().value().getAsDouble() + " below " + minimum);
    String coverage = t.metadata().get(COVERAGE);
    if (coverage != null) {
      try {
        double c = Double.parseDouble(coverage);
        if (c < minimum) return Optional.of("coverage " + coverage + " below " + minimum);
      } catch (NumberFormatException e) {
        return Optional.of("unreadable coverage metadata");
      }
    }
    return Optional.empty();
  }

  /** Same language when the primary languages match and the target names no other script. */
  static boolean sameLanguage(LanguageTag source, LanguageTag target) {
    if (source.isUndetermined() || "mul".equals(source.primaryLanguage())) return false;
    if (!source.primaryLanguage().equals(target.primaryLanguage())) return false;
    String targetScript = target.toLocale().getScript();
    return targetScript.isEmpty() || targetScript.equals(source.toLocale().getScript());
  }

  private static boolean acquire(Semaphore semaphore, long deadline) throws InterruptedException {
    long remaining = deadline - System.nanoTime();
    return remaining > 0 && semaphore.tryAcquire(remaining, TimeUnit.NANOSECONDS);
  }

  private static TargetResult invalid(TargetLanguage entry, String ref) {
    return new TargetResult(
        entry.configured(),
        Optional.empty(),
        Optional.empty(),
        ref,
        TargetStatus.INVALID_LANGUAGE,
        Optional.empty(),
        Optional.empty(),
        Confidence.unknown(),
        Optional.of(entry.resolution().name() + ": " + entry.note()),
        Optional.empty(),
        true,
        false,
        0);
  }

  private static TargetResult problem(
      TargetLanguage entry,
      Optional<LanguageTag> sourceLanguage,
      String ref,
      TargetStatus status,
      String reason,
      long started) {
    return new TargetResult(
        entry.configured(),
        entry.language(),
        sourceLanguage,
        ref,
        status,
        Optional.empty(),
        Optional.empty(),
        Confidence.unknown(),
        Optional.of(reason),
        Optional.empty(),
        true,
        false,
        millis(started));
  }

  private static TargetResult failed(
      TargetLanguage entry, Request request, String ref, String reason, long started) {
    LanguageTag source = request.source().tag();
    return new TargetResult(
        entry.configured(),
        entry.language(),
        source.isUndetermined() ? Optional.empty() : Optional.of(source),
        ref,
        TargetStatus.FAILED,
        Optional.empty(),
        Optional.empty(),
        Confidence.unknown(),
        Optional.of(reason),
        Optional.empty(),
        true,
        false,
        started == 0 ? 0 : millis(started));
  }

  private static ExpansionResult result(
      ExpansionResult.Status status,
      Request request,
      String ref,
      List<TargetLanguage> configured,
      List<TargetResult> targets,
      long started,
      boolean useVariants,
      List<String> explanations) {
    ExpansionPolicy policy = request.settings().policy();
    List<String> texts = new ArrayList<>();
    Set<String> keys = new LinkedHashSet<>();
    texts.add(request.sourceText());
    keys.add(key(request.sourceText()));
    List<TargetResult> marked = new ArrayList<>();
    for (TargetResult r : targets) {
      // A variant equal to the source or to an earlier variant is searched once, via the first.
      boolean used =
          useVariants
              && r.status().usable(policy)
              && r.text().isPresent()
              && keys.add(key(r.text().get()));
      if (used) texts.add(r.text().get());
      marked.add(used ? r.withRetrieval(true) : r);
    }
    return new ExpansionResult(
        status,
        policy,
        request.original(),
        ref,
        request.sourceText(),
        request.source(),
        configured,
        marked,
        texts,
        explanations,
        millis(started));
  }

  /** Duplicate detection for retrieval texts: NFC, case and whitespace insensitive. */
  static String key(String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFC)
        .toLowerCase(Locale.ROOT)
        .strip()
        .replaceAll("\\s+", " ");
  }

  static String reference(String original) {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256").digest(original.getBytes(StandardCharsets.UTF_8));
      return "sha256:" + HexFormat.of().formatHex(hash, 0, 8);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by the Java platform", e);
    }
  }

  private static Duration min(Duration a, Duration b) {
    return a.compareTo(b) <= 0 ? a : b;
  }

  private static long millis(long started) {
    return Duration.ofNanos(System.nanoTime() - started).toMillis();
  }
}

package io.qwenbridge.core.translation;

import io.qwenbridge.core.extraction.EntityDictionary;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.translation.TranslationResult.Failed;
import io.qwenbridge.core.translation.TranslationResult.Failure;
import io.qwenbridge.core.translation.TranslationResult.SkipReason;
import io.qwenbridge.core.translation.TranslationResult.Skipped;
import io.qwenbridge.core.translation.TranslationResult.Translated;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The optional translation stage (QB-015–QB-019). Core works without it: with no provider every
 * request gets an explicit {@link Failure#NO_PROVIDER} result and the original text is used.
 *
 * <p>The stage, not the provider, owns the rules:
 *
 * <ol>
 *   <li>nothing is translated into its own language or from an undetermined language unless the
 *       provider detects languages (no guessing);
 *   <li>capabilities and input size are checked before the call;
 *   <li>the time budget is enforced, and exceptions become {@link Failure#PROVIDER_ERROR};
 *   <li>output is checked: non-blank, for the requested target, of plausible length;
 *   <li>meaning is checked with {@link PreservationChecker}; under {@link Policy#ENFORCE} a changed
 *       translation is rejected as {@link Failure#NOT_PRESERVED}.
 * </ol>
 */
public final class TranslationStage {

  public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

  /** What to do when a translation does not preserve meaning. */
  public enum Policy {
    /** Reject it: the result becomes {@code Failed(NOT_PRESERVED)}. */
    ENFORCE,
    /** Keep it and report; the caller decides (for example a stricter domain check follows). */
    REPORT
  }

  private final Optional<TranslationProvider> provider;
  private final Duration timeout;

  public TranslationStage(Optional<TranslationProvider> provider, Duration timeout) {
    this.provider = provider == null ? Optional.empty() : provider;
    this.timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
    if (this.timeout.isNegative() || this.timeout.isZero()) {
      throw new IllegalArgumentException("timeout must be positive");
    }
  }

  /** A stage without a provider: every request is answered with {@code NO_PROVIDER}. */
  public static TranslationStage disabled() {
    return new TranslationStage(Optional.empty(), DEFAULT_TIMEOUT);
  }

  public boolean available() {
    return provider.isPresent();
  }

  public Optional<TranslationCapabilities> capabilities() {
    return provider.map(TranslationProvider::capabilities);
  }

  public TranslationOutcome translate(
      String text,
      LanguageTag source,
      Optional<LanguageTag> target,
      EntityDictionary dictionary,
      Policy policy) {
    Objects.requireNonNull(text, "text must not be null");
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(policy, "policy must not be null");
    long started = System.nanoTime();
    if (target.isEmpty() || target.get().isUndetermined()) {
      return done(new Skipped(SkipReason.NOT_REQUESTED), started);
    }
    LanguageTag to = target.get();
    if (!source.isUndetermined() && source.primaryLanguage().equals(to.primaryLanguage())) {
      return done(new Skipped(SkipReason.SAME_LANGUAGE), started);
    }
    if (provider.isEmpty()) {
      return done(Failed.of(Failure.NO_PROVIDER, null), started);
    }
    TranslationProvider p = provider.get();
    TranslationCapabilities caps = p.capabilities();
    if (source.isUndetermined() && !caps.detectsSource()) {
      return done(Failed.of(Failure.UNDETERMINED_SOURCE, p.id()), started);
    }
    if (!caps.supportsPair(source, to)) {
      return done(Failed.of(Failure.UNSUPPORTED_LANGUAGE_PAIR, p.id()), started);
    }
    if (text.length() > caps.maxInputChars()) {
      return done(Failed.of(Failure.INPUT_TOO_LARGE, p.id()), started);
    }

    TranslationResult result = call(p, new TranslationRequest(text, source, to, timeout));
    if (!(result instanceof Translated translated)) {
      return done(result, started);
    }
    if (!acceptable(translated, text, to)) {
      return done(Failed.of(Failure.INVALID_OUTPUT, p.id()), started);
    }
    PreservationReport report =
        PreservationChecker.check(
            text, translated.source(), translated.text(), translated.target(), dictionary);
    TranslationResult finalResult =
        policy == Policy.ENFORCE && !report.preserved()
            ? Failed.of(Failure.NOT_PRESERVED, p.id())
            : translated;
    return new TranslationOutcome(finalResult, Optional.of(report), elapsed(started));
  }

  private TranslationResult call(TranslationProvider p, TranslationRequest request) {
    CompletableFuture<TranslationResult> future = new CompletableFuture<>();
    Thread worker =
        Thread.ofVirtual()
            .name("qwenbridge-translation")
            .start(
                () -> {
                  try {
                    future.complete(p.translate(request));
                  } catch (RuntimeException e) {
                    future.completeExceptionally(e);
                  }
                });
    try {
      TranslationResult result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      return result == null ? Failed.of(Failure.INVALID_OUTPUT, p.id()) : result;
    } catch (TimeoutException e) {
      worker.interrupt();
      return Failed.of(Failure.TIMEOUT, p.id());
    } catch (ExecutionException e) {
      return Failed.of(Failure.PROVIDER_ERROR, p.id());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      worker.interrupt();
      return Failed.of(Failure.TIMEOUT, p.id());
    }
  }

  /** Rejects output that cannot be a translation of the input into the requested language. */
  private static boolean acceptable(Translated t, String input, LanguageTag target) {
    return !t.text().isBlank()
        && t.target().primaryLanguage().equals(target.primaryLanguage())
        && t.text().length() <= Math.max(4 * input.length(), input.length() + 256);
  }

  private static TranslationOutcome done(TranslationResult result, long started) {
    return new TranslationOutcome(result, Optional.empty(), elapsed(started));
  }

  private static Duration elapsed(long started) {
    return Duration.ofNanos(System.nanoTime() - started);
  }
}

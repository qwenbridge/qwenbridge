package io.qwenbridge.core.expansion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.expansion.ExpansionResult.Status;
import io.qwenbridge.core.expansion.TargetLanguage.Resolution;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.translation.ProviderAvailability;
import io.qwenbridge.core.translation.TranslationCapabilities;
import io.qwenbridge.core.translation.TranslationProvider;
import io.qwenbridge.core.translation.TranslationResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Orchestration tests with deterministic providers (QB-286 test matrix 1–25, 28, 33, 34). */
class QueryExpanderTest {

  static final String QUERY = "I need a red winter jacket under 100 euros";
  static final ExpansionLimits LIMITS =
      new ExpansionLimits(Duration.ofSeconds(2), Duration.ofSeconds(5), 4);

  static ExpansionSettings settings(ExpansionPolicy policy, String... targets) {
    return new ExpansionSettings(
        true, List.of(targets), policy, Map.of(), ExpansionSettings.DEFAULT_MIN_CONFIDENCE);
  }

  static ExpansionResult expand(
      String text, SourceLanguage source, ExpansionSettings s, List<TranslationProvider> p) {
    return QueryExpander.expand(
        new QueryExpander.Request(text, text, source, s, null),
        p,
        LIMITS,
        Optional.empty(),
        () -> false);
  }

  static ExpansionResult en(ExpansionSettings s, TranslationProvider... p) {
    return expand(QUERY, SourceLanguage.of(new LanguageTag("en")), s, List.of(p));
  }

  static List<String> statuses(ExpansionResult r) {
    return r.targets().stream().map(t -> t.status().name()).toList();
  }

  static List<String> languages(ExpansionResult r) {
    return r.targets().stream().map(t -> t.language().map(LanguageTag::value).orElse("-")).toList();
  }

  @Nested
  class Configuration {
    @Test
    void disabledExecutesNothing() {
      var p = FakeProvider.labelling("p");
      var r =
          QueryExpander.expand(
              new QueryExpander.Request(
                  QUERY, QUERY, SourceLanguage.undetermined(), ExpansionSettings.disabled(), null),
              List.of(p),
              LIMITS,
              Optional.empty(),
              () -> false);
      assertEquals(Status.DISABLED, r.status());
      assertFalse(r.reportable());
      assertEquals(0, p.calls.get());
      assertEquals(List.of(QUERY), r.retrievalTexts());
    }

    @Test
    void zeroTargetsIsNotConfiguredAndAssumesNothing() {
      var p = FakeProvider.labelling("p");
      var r = en(settings(ExpansionPolicy.ALL_TARGETS), p);
      assertEquals(Status.NOT_CONFIGURED, r.status());
      assertTrue(r.targets().isEmpty());
      assertEquals(0, p.calls.get());
      assertEquals(QUERY, r.original());
      assertEquals(List.of(QUERY), r.retrievalTexts());
    }

    @Test
    void oneTwoFiveAndTenTargetsGiveOneResultEachInConfiguredOrder() {
      List<String> ten = List.of("fa", "sv", "nb", "nn", "de", "fr", "es", "it", "ja", "zh-Hans");
      for (int n : new int[] {1, 2, 5, 10}) {
        var targets = ten.subList(0, n);
        var r =
            en(
                settings(ExpansionPolicy.ALL_TARGETS, targets.toArray(String[]::new)),
                FakeProvider.labelling("p"));
        assertEquals(n, r.targets().size(), "results for " + n);
        assertEquals(targets, languages(r));
        assertTrue(r.targets().stream().allMatch(t -> t.status() == TargetStatus.SUCCESS));
        assertEquals(Status.COMPLETE, r.status());
        assertEquals(n + 1, r.retrievalTexts().size(), "source plus one variant per target");
        assertTrue(r.targets().stream().allMatch(t -> t.originalRef().equals(r.originalRef())));
      }
    }

    @Test
    void duplicatesAreReportedOnceAndCaseIsNormalized() {
      var r =
          en(
              settings(ExpansionPolicy.ALL_TARGETS, "EN", "FA", "NB", "NN", "fa", " en "),
              FakeProvider.labelling("p"));
      assertEquals(List.of("en", "fa", "nb", "nn"), languages(r));
      var resolutions = r.configured().stream().map(TargetLanguage::resolution).toList();
      assertEquals(
          List.of(
              Resolution.NORMALIZED,
              Resolution.NORMALIZED,
              Resolution.NORMALIZED,
              Resolution.NORMALIZED,
              Resolution.DUPLICATE,
              Resolution.DUPLICATE),
          resolutions);
      assertTrue(r.configured().get(4).note().contains("FA"), r.configured().get(4).note());
      assertEquals(TargetStatus.UNCHANGED, r.targets().getFirst().status(), "en source to en");
    }

    @Test
    void seIsSwedishOnlyThroughAnExplicitAlias() {
      var withAlias = TargetLanguages.resolve(List.of("SE", "se", "sv"), Map.of("SE", "sv"));
      assertEquals(Resolution.ALIAS, withAlias.get(0).resolution());
      assertEquals("sv", withAlias.get(0).language().orElseThrow().value());
      assertEquals(Resolution.VALID, withAlias.get(1).resolution());
      assertEquals("se", withAlias.get(1).language().orElseThrow().value(), "se is Northern Sami");
      assertEquals(Resolution.DUPLICATE, withAlias.get(2).resolution(), "sv after SE->sv");

      var withoutAlias = TargetLanguages.resolve(List.of("SE"), Map.of());
      assertEquals(Resolution.AMBIGUOUS_REGION_CODE, withoutAlias.getFirst().resolution());
    }

    @Test
    void invalidAndAmbiguousIdentifiersGetAnExplicitResult() {
      var r =
          en(
              settings(ExpansionPolicy.ALL_TARGETS, "english", "e_n", "", "NO", "DK", "und", "sv"),
              FakeProvider.labelling("p"));
      var resolutions = r.configured().stream().map(TargetLanguage::resolution).toList();
      assertEquals(
          List.of(
              Resolution.INVALID,
              Resolution.INVALID,
              Resolution.INVALID,
              Resolution.AMBIGUOUS_REGION_CODE,
              Resolution.INVALID,
              Resolution.NOT_A_TARGET,
              Resolution.VALID),
          resolutions);
      assertEquals(7, r.targets().size(), "never silently omitted");
      assertEquals(
          List.of(
              "INVALID_LANGUAGE",
              "INVALID_LANGUAGE",
              "INVALID_LANGUAGE",
              "INVALID_LANGUAGE",
              "INVALID_LANGUAGE",
              "INVALID_LANGUAGE",
              "SUCCESS"),
          statuses(r));
      assertTrue(r.configured().get(4).note().contains("region code"));
      assertEquals(Status.PARTIAL, r.status());
    }
  }

  @Nested
  class SourceLanguages {
    @Test
    void sameLanguageIsUnchangedWithoutAProviderCall() {
      var p = FakeProvider.labelling("p");
      var r = en(settings(ExpansionPolicy.ALL_TARGETS, "en"), p);
      assertEquals(TargetStatus.UNCHANGED, r.targets().getFirst().status());
      assertEquals(QUERY, r.targets().getFirst().text().orElseThrow());
      assertEquals(0, p.calls.get());
      assertEquals(List.of(QUERY), r.retrievalTexts(), "no duplicate of the source text");
    }

    @Test
    void sourceOutsideTheTargetsIsTranslatedToEveryTarget() {
      var r =
          expand(
              "jag behöver en röd vinterjacka",
              SourceLanguage.of(new LanguageTag("sv")),
              settings(ExpansionPolicy.ALL_TARGETS, "en", "fa", "de"),
              List.of(FakeProvider.labelling("p")));
      assertEquals(List.of("SUCCESS", "SUCCESS", "SUCCESS"), statuses(r));
      assertTrue(
          r.targets().stream()
              .allMatch(t -> t.sourceLanguage().orElseThrow().value().equals("sv")));
    }

    @Test
    void mixedSourceNeedsAProviderThatAcceptsMixedText() {
      var mixed =
          new SourceLanguage(
              Optional.empty(),
              false,
              true,
              List.of(new LanguageTag("en"), new LanguageTag("fa")),
              Confidence.heuristic());
      var any = FakeProvider.labelling("any");
      var r =
          expand(
              "red ژاکت under 100 euros",
              mixed,
              settings(ExpansionPolicy.ALL_TARGETS, "sv"),
              List.of(any));
      assertEquals(TargetStatus.SUCCESS, r.targets().getFirst().status());
      assertEquals("mul", r.targets().getFirst().sourceLanguage().orElseThrow().value());

      var enOnly =
          new FakeProvider(
              "en-only",
              new TranslationCapabilities(Set.of("en"), Set.of("sv"), false, 2000),
              ProviderAvailability.ready(),
              req -> FakeProvider.translated(req, "x"));
      r =
          expand(
              "red ژاکت under 100 euros",
              mixed,
              settings(ExpansionPolicy.ALL_TARGETS, "sv"),
              List.of(enOnly));
      assertEquals(TargetStatus.UNSUPPORTED_LANGUAGE_PAIR, r.targets().getFirst().status());
      assertTrue(r.targets().getFirst().reason().orElseThrow().startsWith("mixed-language source"));
      assertEquals(0, enOnly.calls.get());
    }

    @Test
    void unsupportedAndUndeterminedSources() {
      var enOnly =
          new FakeProvider(
              "en-only",
              new TranslationCapabilities(Set.of("en"), Set.of("sv"), false, 2000),
              ProviderAvailability.ready(),
              req -> FakeProvider.translated(req, "x"));
      var r =
          expand(
              "赤い冬のジャケット",
              SourceLanguage.of(new LanguageTag("ja")),
              settings(ExpansionPolicy.ALL_TARGETS, "sv"),
              List.of(enOnly));
      assertEquals(TargetStatus.UNSUPPORTED_LANGUAGE_PAIR, r.targets().getFirst().status());

      r =
          expand(
              "qwzx",
              SourceLanguage.undetermined(),
              settings(ExpansionPolicy.ALL_TARGETS, "sv"),
              List.of(enOnly));
      assertEquals(TargetStatus.SOURCE_UNDETERMINED, r.targets().getFirst().status());
      assertTrue(r.targets().getFirst().sourceLanguage().isEmpty(), "no invented source language");
      assertEquals(0, enOnly.calls.get());

      var unreliable =
          new SourceLanguage(
              Optional.of(new LanguageTag("sv")), false, false, List.of(), Confidence.heuristic());
      r = expand("qwzx", unreliable, settings(ExpansionPolicy.ALL_TARGETS, "sv"), List.of(enOnly));
      assertEquals(
          TargetStatus.SOURCE_UNDETERMINED,
          r.targets().getFirst().status(),
          "an unreliable guess is not a source");
    }
  }

  @Nested
  class Providers {
    @Test
    void unsupportedTargetAndUnsupportedPair() {
      var pairs =
          new FakeProvider(
              "pairs",
              new TranslationCapabilities(
                  Set.of("en", "sv", "fa"),
                  Set.of("en", "sv"),
                  false,
                  2000,
                  Set.of("en>sv", "sv>en")),
              ProviderAvailability.ready(),
              req -> FakeProvider.translated(req, "[" + req.target() + "] " + req.text()));
      var r =
          expand(
              "من یک ژاکت می‌خواهم",
              SourceLanguage.of(new LanguageTag("fa")),
              settings(ExpansionPolicy.ALL_TARGETS, "sv", "ja"),
              List.of(pairs));
      assertEquals(List.of("UNSUPPORTED_LANGUAGE_PAIR", "UNSUPPORTED_LANGUAGE"), statuses(r));
      assertEquals(Status.FAILED, r.status());
      assertEquals(0, pairs.calls.get());
      r = en(settings(ExpansionPolicy.ALL_TARGETS, "sv"), pairs);
      assertEquals(TargetStatus.SUCCESS, r.targets().getFirst().status());
    }

    @Test
    void missingProviderAndMissingResourcesAreReportedNotGuessed() {
      var r = en(settings(ExpansionPolicy.ALL_TARGETS, "sv", "fa"));
      assertEquals(List.of("PROVIDER_UNAVAILABLE", "PROVIDER_UNAVAILABLE"), statuses(r));
      assertEquals("NO_PROVIDER", r.targets().getFirst().reason().orElseThrow());
      assertEquals(List.of(QUERY), r.retrievalTexts());

      var missing =
          new FakeProvider(
              "local-model",
              TranslationCapabilities.anyToAny(2000),
              ProviderAvailability.of(
                  ProviderAvailability.State.MISSING_RESOURCES, "model files not found"),
              req -> FakeProvider.translated(req, "x"));
      r = en(settings(ExpansionPolicy.ALL_TARGETS, "sv"), missing);
      assertEquals(TargetStatus.PROVIDER_UNAVAILABLE, r.targets().getFirst().status());
      assertTrue(r.targets().getFirst().reason().orElseThrow().contains("MISSING_RESOURCES"));
      assertEquals(0, missing.calls.get());
    }

    @Test
    void aFailingProviderFallsThroughToTheNextCapableOne() {
      var broken =
          new FakeProvider(
              "broken",
              TranslationCapabilities.anyToAny(2000),
              ProviderAvailability.ready(),
              req -> {
                throw new IllegalStateException("boom");
              });
      var r =
          en(settings(ExpansionPolicy.ALL_TARGETS, "sv"), broken, FakeProvider.labelling("backup"));
      assertEquals(TargetStatus.SUCCESS, r.targets().getFirst().status());
      assertEquals("backup", r.targets().getFirst().provider().orElseThrow());
      assertEquals(1, broken.calls.get());
    }
  }

  @Nested
  class Policies {
    final FakeProvider svOnly =
        new FakeProvider(
            "sv-only",
            TranslationCapabilities.anyToAny(2000),
            ProviderAvailability.ready(),
            req ->
                req.target().value().equals("sv")
                    ? FakeProvider.translated(req, "[sv] " + req.text())
                    : TranslationResult.Failed.of(
                        TranslationResult.Failure.PROVIDER_ERROR, "sv-only"));

    @Test
    void partialFailureKeepsTheSuccessfulTargets() {
      var r = en(settings(ExpansionPolicy.ALL_TARGETS, "sv", "fa", "nb"), svOnly);
      assertEquals(List.of("SUCCESS", "FAILED", "FAILED"), statuses(r));
      assertEquals(Status.PARTIAL, r.status());
      assertEquals(List.of(QUERY, "[sv] " + QUERY), r.retrievalTexts());
      assertEquals("PROVIDER_ERROR", r.targets().get(1).reason().orElseThrow());
    }

    @Test
    void strictFailsUnlessEveryTargetSucceedsButStillReportsEach() {
      var r = en(settings(ExpansionPolicy.STRICT_ALL_TARGETS, "sv", "fa"), svOnly);
      assertEquals(Status.FAILED, r.status());
      assertEquals(List.of("SUCCESS", "FAILED"), statuses(r));
      assertEquals(List.of(QUERY), r.retrievalTexts(), "no variant used after a strict failure");
      assertFalse(r.targets().getFirst().retrieval());
      r = en(settings(ExpansionPolicy.STRICT_ALL_TARGETS, "sv", "en"), svOnly);
      assertEquals(Status.COMPLETE, r.status());
    }

    @Test
    void bestEffortAlsoUsesLowConfidenceVariants() {
      var lowFa =
          new FakeProvider(
              "low",
              TranslationCapabilities.anyToAny(2000),
              ProviderAvailability.ready(),
              req ->
                  req.target().value().equals("fa")
                      ? FakeProvider.translated(
                          req, "[fa] " + req.text(), Confidence.modelScore(0.2), Map.of())
                      : FakeProvider.translated(req, "[" + req.target() + "] " + req.text()));
      var all = en(settings(ExpansionPolicy.ALL_TARGETS, "sv", "fa"), lowFa);
      assertEquals(List.of("SUCCESS", "LOW_CONFIDENCE"), statuses(all));
      assertEquals(2, all.retrievalTexts().size(), "ALL_TARGETS does not use low confidence");
      assertTrue(all.targets().get(1).text().isPresent(), "the low-confidence text is reported");
      var best = en(settings(ExpansionPolicy.BEST_EFFORT_ALL_TARGETS, "sv", "fa"), lowFa);
      assertEquals(3, best.retrievalTexts().size());
      assertTrue(best.targets().get(1).retrieval());
    }

    @Test
    void identicalVariantsAreUsedOnce() {
      var same =
          new FakeProvider(
              "same",
              TranslationCapabilities.anyToAny(2000),
              ProviderAvailability.ready(),
              req -> FakeProvider.translated(req, "Red Winter Jacket under 100 euros"));
      var r = en(settings(ExpansionPolicy.ALL_TARGETS, "nb", "nn"), same);
      assertEquals(2, r.retrievalTexts().size(), "case-insensitive duplicate collapsed");
      assertTrue(r.targets().get(0).retrieval());
      assertFalse(r.targets().get(1).retrieval());
    }
  }

  @Nested
  class Preservation {
    static ExpansionResult rewrite(String query, String output) {
      var p =
          new FakeProvider(
              "rewriter",
              TranslationCapabilities.anyToAny(2000),
              ProviderAvailability.ready(),
              req -> FakeProvider.translated(req, output));
      return expand(
          query,
          SourceLanguage.of(new LanguageTag("en")),
          settings(ExpansionPolicy.ALL_TARGETS, "sv"),
          List.of(p));
    }

    @Test
    void technicalIdentifiersMustSurviveVerbatim() {
      String q = "show errors from redis03-pt1 in the last 30 minutes";
      var bad = rewrite(q, "visa fel från redis-03-pt1 de senaste 30 minuterna");
      assertEquals(TargetStatus.FAILED, bad.targets().getFirst().status());
      assertTrue(bad.targets().getFirst().reason().orElseThrow().startsWith("NOT_PRESERVED"));
      assertFalse(bad.targets().getFirst().identifiersPreserved());
      var good = rewrite(q, "visa fel från redis03-pt1 de senaste 30 minuterna");
      assertEquals(TargetStatus.SUCCESS, good.targets().getFirst().status());
      var host =
          rewrite(
              "ping db.prod.example.com at 10.0.0.12", "pinga db.prod.exempel.com på 10.0.0.12");
      assertEquals(TargetStatus.FAILED, host.targets().getFirst().status());
    }

    @Test
    void currencyNumbersAndNegationAreNotChanged() {
      var currency = rewrite("red jacket under 100 EUR", "röd jacka under 100 USD");
      assertEquals(TargetStatus.FAILED, currency.targets().getFirst().status());
      var number = rewrite("red jacket under 100 EUR", "röd jacka under 1000 EUR");
      assertEquals(TargetStatus.FAILED, number.targets().getFirst().status());
      assertTrue(number.targets().getFirst().reason().orElseThrow().contains("NUMBER"));
      var negation = rewrite("jacket without leather", "jacka med läder");
      assertEquals(TargetStatus.FAILED, negation.targets().getFirst().status());
      assertTrue(
          negation.targets().getFirst().reason().orElseThrow().contains("NEGATION"),
          negation.targets().getFirst().reason().orElseThrow());
      var kept = rewrite("red jacket under 100 EUR", "röd jacka under 100 EUR");
      assertEquals(TargetStatus.SUCCESS, kept.targets().getFirst().status());
    }

    @Test
    void quotedValuesMustSurvive() {
      var r = rewrite("jacket named \"Winter Hero\"", "jacka som heter \"Vinterhjälte\"");
      assertEquals(TargetStatus.FAILED, r.targets().getFirst().status());
    }
  }

  @Nested
  class Bounds {
    static FakeProvider slow(String slowTarget, long millis) {
      return new FakeProvider(
          "slow",
          TranslationCapabilities.anyToAny(2000),
          ProviderAvailability.ready(),
          req -> {
            if (req.target().value().equals(slowTarget) || slowTarget.equals("*")) {
              try {
                Thread.sleep(millis);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            }
            return FakeProvider.translated(req, "[" + req.target() + "] " + req.text());
          });
    }

    @Test
    void aSlowLanguageTimesOutWithoutFailingTheOthers() {
      var limits = new ExpansionLimits(Duration.ofMillis(200), Duration.ofSeconds(5), 4);
      long t0 = System.nanoTime();
      var r =
          QueryExpander.expand(
              new QueryExpander.Request(
                  QUERY,
                  QUERY,
                  SourceLanguage.of(new LanguageTag("en")),
                  settings(ExpansionPolicy.ALL_TARGETS, "sv", "fa", "nb"),
                  null),
              List.of(slow("fa", 3_000)),
              limits,
              Optional.empty(),
              () -> false);
      long ms = (System.nanoTime() - t0) / 1_000_000;
      assertEquals(List.of("SUCCESS", "FAILED", "SUCCESS"), statuses(r));
      assertEquals("TIMEOUT", r.targets().get(1).reason().orElseThrow());
      assertTrue(ms < 2_000, "bounded by the per-language timeout: " + ms + " ms");
    }

    @Test
    void theOverallDeadlineReportsUnfinishedTargets() {
      var limits = new ExpansionLimits(Duration.ofSeconds(5), Duration.ofMillis(300), 1);
      long t0 = System.nanoTime();
      var r =
          QueryExpander.expand(
              new QueryExpander.Request(
                  QUERY,
                  QUERY,
                  SourceLanguage.of(new LanguageTag("en")),
                  settings(ExpansionPolicy.ALL_TARGETS, "sv", "fa", "nb", "nn"),
                  null),
              List.of(slow("*", 250)),
              limits,
              Optional.empty(),
              () -> false);
      long ms = (System.nanoTime() - t0) / 1_000_000;
      assertEquals(4, r.targets().size());
      assertTrue(
          r.targets().stream()
              .anyMatch(
                  t ->
                      t.status() == TargetStatus.FAILED
                          && t.reason().orElseThrow().equals("TIMEOUT")),
          statuses(r).toString());
      assertTrue(ms < 1_500, "overall deadline honoured: " + ms + " ms");
    }

    @Test
    void cancellationStopsAndReportsEveryTarget() throws Exception {
      var cancelled = new AtomicBoolean();
      var p = slow("*", 2_000);
      Thread.ofVirtual()
          .start(
              () -> {
                try {
                  Thread.sleep(150);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                cancelled.set(true);
              });
      long t0 = System.nanoTime();
      var r =
          QueryExpander.expand(
              new QueryExpander.Request(
                  QUERY,
                  QUERY,
                  SourceLanguage.of(new LanguageTag("en")),
                  settings(ExpansionPolicy.ALL_TARGETS, "sv", "fa", "nb"),
                  null),
              List.of(p),
              LIMITS,
              Optional.empty(),
              cancelled::get);
      long ms = (System.nanoTime() - t0) / 1_000_000;
      assertEquals(Status.CANCELLED, r.status());
      assertEquals(3, r.targets().size());
      assertTrue(
          r.targets().stream().allMatch(t -> t.reason().orElse("").equals("CANCELLED")),
          statuses(r).toString());
      assertTrue(ms < 1_000, "stopped promptly: " + ms + " ms");
      Thread.sleep(100);
      assertEquals(0, p.active.get(), "workers interrupted and finished");
    }

    @Test
    void concurrencyIsBoundedPerRequestAndProcessWide() throws Exception {
      var p = slow("*", 50);
      var limits = new ExpansionLimits(Duration.ofSeconds(2), Duration.ofSeconds(10), 3);
      var r =
          QueryExpander.expand(
              new QueryExpander.Request(
                  QUERY,
                  QUERY,
                  SourceLanguage.of(new LanguageTag("en")),
                  settings(
                      ExpansionPolicy.ALL_TARGETS,
                      "sv",
                      "fa",
                      "nb",
                      "nn",
                      "de",
                      "fr",
                      "es",
                      "it",
                      "ja",
                      "ko"),
                  null),
              List.of(p),
              limits,
              Optional.empty(),
              () -> false);
      assertEquals(Status.COMPLETE, r.status());
      assertTrue(p.maxActive.get() <= 3, "per request: " + p.maxActive.get());

      var shared = new Semaphore(2);
      var q = slow("*", 50);
      ExecutorService pool = Executors.newFixedThreadPool(8);
      List<Future<ExpansionResult>> runs = new ArrayList<>();
      for (int i = 0; i < 8; i++) {
        String text = "query number " + i;
        runs.add(
            pool.submit(
                () ->
                    QueryExpander.expand(
                        new QueryExpander.Request(
                            text,
                            text,
                            SourceLanguage.of(new LanguageTag("en")),
                            settings(ExpansionPolicy.ALL_TARGETS, "sv", "fa"),
                            null),
                        List.of(q),
                        new ExpansionLimits(Duration.ofSeconds(2), Duration.ofSeconds(20), 2),
                        Optional.of(shared),
                        () -> false)));
      }
      List<String> seen = Collections.synchronizedList(new ArrayList<>());
      for (int i = 0; i < runs.size(); i++) {
        var result = runs.get(i).get();
        assertEquals(Status.COMPLETE, result.status());
        assertEquals(
            "[sv] query number " + i,
            result.targets().getFirst().text().orElseThrow(),
            "no cross-request leakage");
        seen.add(result.originalRef());
      }
      pool.shutdown();
      assertTrue(q.maxActive.get() <= 2, "process-wide: " + q.maxActive.get());
      assertEquals(8, Set.copyOf(seen).size());
      assertEquals(2, shared.availablePermits(), "every permit released");
    }
  }
}

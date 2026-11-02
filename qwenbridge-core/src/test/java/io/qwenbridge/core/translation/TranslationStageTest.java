package io.qwenbridge.core.translation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.extraction.EntityDictionary;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.translation.TranslationResult.Failed;
import io.qwenbridge.core.translation.TranslationResult.Failure;
import io.qwenbridge.core.translation.TranslationResult.SkipReason;
import io.qwenbridge.core.translation.TranslationResult.Skipped;
import io.qwenbridge.core.translation.TranslationResult.Translated;
import io.qwenbridge.core.translation.TranslationStage.Policy;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** QB-015, QB-016, QB-019: the optional stage, its typed outcomes and honest confidence. */
class TranslationStageTest {

  static final LanguageTag EN = new LanguageTag("en");
  static final LanguageTag SV = new LanguageTag("sv");
  static final LanguageTag FA = new LanguageTag("fa");

  /** A deterministic fixture provider: a phrase table, never a model. */
  static TranslationProvider table(Map<String, String> phrases, TranslationCapabilities caps) {
    return provider(
        caps,
        r ->
            phrases.containsKey(r.text())
                ? new Translated(
                    phrases.get(r.text()),
                    r.source(),
                    r.target(),
                    "table",
                    Confidence.unknown(),
                    Map.of())
                : Failed.of(Failure.PROVIDER_ERROR, "table"));
  }

  static TranslationProvider provider(
      TranslationCapabilities caps, Function<TranslationRequest, TranslationResult> fn) {
    return new TranslationProvider() {
      @Override
      public String id() {
        return "table";
      }

      @Override
      public TranslationCapabilities capabilities() {
        return caps;
      }

      @Override
      public TranslationResult translate(TranslationRequest request) {
        return fn.apply(request);
      }
    };
  }

  static TranslationStage stage(TranslationProvider provider) {
    return new TranslationStage(Optional.of(provider), Duration.ofSeconds(2));
  }

  static TranslationOutcome run(TranslationStage stage, String text, LanguageTag from) {
    return stage.translate(text, from, Optional.of(EN), EntityDictionary.EMPTY, Policy.ENFORCE);
  }

  @Nested
  class OptionalStage {

    @Test
    void withoutProviderTheResultIsExplicitlyUnsupported() {
      TranslationOutcome outcome = run(TranslationStage.disabled(), "skor under 100 EUR", SV);
      Failed failed = assertInstanceOf(Failed.class, outcome.result());
      assertEquals(Failure.NO_PROVIDER, failed.failure());
      assertTrue(failed.failure().unsupported());
      assertTrue(outcome.translated().isEmpty());
    }

    @Test
    void noTargetMeansNothingIsRequested() {
      TranslationOutcome outcome =
          TranslationStage.disabled()
              .translate("x", SV, Optional.empty(), EntityDictionary.EMPTY, Policy.ENFORCE);
      assertEquals(new Skipped(SkipReason.NOT_REQUESTED), outcome.result());
    }

    @Test
    void sameLanguageIsSkippedWithoutCallingTheProvider() {
      AtomicInteger calls = new AtomicInteger();
      TranslationStage stage =
          stage(
              provider(
                  TranslationCapabilities.anyToAny(1000),
                  r -> {
                    calls.incrementAndGet();
                    return Failed.of(Failure.PROVIDER_ERROR, "x");
                  }));
      assertEquals(
          new Skipped(SkipReason.SAME_LANGUAGE),
          run(stage, "shoes", new LanguageTag("en-GB")).result());
      assertEquals(0, calls.get());
    }

    @Test
    void undeterminedSourceIsNotGuessed() {
      TranslationStage stage = stage(table(Map.of(), TranslationCapabilities.anyToAny(1000)));
      Failed failed =
          assertInstanceOf(Failed.class, run(stage, "xyz", LanguageTag.UNDETERMINED).result());
      assertEquals(Failure.UNDETERMINED_SOURCE, failed.failure());
    }
  }

  @Nested
  class CapabilityDiscovery {

    @Test
    void unsupportedPairIsRejectedBeforeTheCall() {
      TranslationCapabilities svOnly =
          new TranslationCapabilities(Set.of("sv"), Set.of("en"), false, 1000);
      TranslationStage stage = stage(table(Map.of(), svOnly));
      assertEquals(
          Failure.UNSUPPORTED_LANGUAGE_PAIR, ((Failed) run(stage, "کفش", FA).result()).failure());
      assertTrue(svOnly.supportsSource(SV));
      assertFalse(svOnly.supportsTarget(SV));
    }

    @Test
    void oversizedInputIsRejected() {
      TranslationStage stage = stage(table(Map.of(), TranslationCapabilities.anyToAny(5)));
      assertEquals(
          Failure.INPUT_TOO_LARGE, ((Failed) run(stage, "skor under 100", SV).result()).failure());
    }

    @Test
    void codeSwitchedTextNeedsAProviderThatAcceptsMul() {
      LanguageTag mul = new LanguageTag("mul");
      TranslationCapabilities named =
          new TranslationCapabilities(Set.of("sv", "fa"), Set.of("en"), false, 1000);
      assertFalse(named.supportsSource(mul));
      assertTrue(TranslationCapabilities.anyToAny(10).supportsSource(mul));
      assertFalse(TranslationCapabilities.anyToAny(10).supportsTarget(mul));
    }
  }

  @Nested
  class TypedFailures {

    @Test
    void timeoutIsEnforcedByTheStage() {
      TranslationStage slow =
          new TranslationStage(
              Optional.of(
                  provider(
                      TranslationCapabilities.anyToAny(1000),
                      r -> {
                        try {
                          Thread.sleep(5_000);
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                        }
                        return Failed.of(Failure.PROVIDER_ERROR, "slow");
                      })),
              Duration.ofMillis(100));
      TranslationOutcome outcome = run(slow, "skor", SV);
      assertEquals(Failure.TIMEOUT, ((Failed) outcome.result()).failure());
      assertTrue(outcome.elapsed().compareTo(Duration.ofSeconds(4)) < 0);
    }

    @Test
    void providerExceptionBecomesProviderError() {
      TranslationStage stage =
          stage(
              provider(
                  TranslationCapabilities.anyToAny(1000),
                  r -> {
                    throw new IllegalStateException("boom");
                  }));
      assertEquals(Failure.PROVIDER_ERROR, ((Failed) run(stage, "skor", SV).result()).failure());
    }

    @Test
    void blankOrWrongLanguageOutputIsInvalid() {
      TranslationStage blank =
          stage(table(Map.of("skor", " "), TranslationCapabilities.anyToAny(99)));
      assertEquals(Failure.INVALID_OUTPUT, ((Failed) run(blank, "skor", SV).result()).failure());
      TranslationStage wrongTarget =
          stage(
              provider(
                  TranslationCapabilities.anyToAny(99),
                  r ->
                      new Translated(
                          "Schuhe",
                          SV,
                          new LanguageTag("de"),
                          "t",
                          Confidence.unknown(),
                          Map.of())));
      assertEquals(
          Failure.INVALID_OUTPUT, ((Failed) run(wrongTarget, "skor", SV).result()).failure());
    }
  }

  @Nested
  class SemanticPreservation {

    @Test
    void faithfulTranslationIsAcceptedWithItsReport() {
      TranslationStage stage =
          stage(
              table(
                  Map.of("skor under 1200 SEK", "shoes under 1200 SEK"),
                  TranslationCapabilities.anyToAny(1000)));
      TranslationOutcome outcome = run(stage, "skor under 1200 SEK", SV);
      assertEquals("shoes under 1200 SEK", outcome.translated().orElseThrow().text());
      assertEquals(
          PreservationReport.Status.PRESERVED, outcome.preservation().orElseThrow().status());
    }

    @Test
    void changedMeaningIsRejectedUnderEnforce() {
      TranslationStage stage =
          stage(
              table(
                  Map.of("skor utan läder under 1200 SEK", "leather shoes under 1200 SEK"),
                  TranslationCapabilities.anyToAny(1000)));
      TranslationOutcome outcome = run(stage, "skor utan läder under 1200 SEK", SV);
      assertEquals(Failure.NOT_PRESERVED, ((Failed) outcome.result()).failure());
      assertFalse(outcome.preservation().orElseThrow().preserved());
    }

    @Test
    void reportPolicyKeepsTheTranslationAndTheIssues() {
      TranslationStage stage =
          stage(
              table(
                  Map.of("skor utan läder", "leather shoes"),
                  TranslationCapabilities.anyToAny(1000)));
      TranslationOutcome outcome =
          stage.translate(
              "skor utan läder", SV, Optional.of(EN), EntityDictionary.EMPTY, Policy.REPORT);
      assertTrue(outcome.translated().isPresent());
      assertFalse(outcome.preservation().orElseThrow().preserved());
    }
  }

  @Nested
  class HonestConfidence {

    @Test
    void confidenceIsWhatTheProviderReportedAndNeverInvented() {
      TranslationStage stage =
          stage(table(Map.of("skor", "shoes"), TranslationCapabilities.anyToAny(99)));
      Translated t = run(stage, "skor", SV).translated().orElseThrow();
      assertEquals(Confidence.Kind.UNKNOWN, t.confidence().kind());
      assertTrue(t.confidence().value().isEmpty());
    }

    @Test
    void aModelScoreIsCarriedAsAScoreNotAProbability() {
      TranslationStage stage =
          stage(
              provider(
                  TranslationCapabilities.anyToAny(99),
                  r ->
                      new Translated(
                          "shoes",
                          r.source(),
                          r.target(),
                          "m",
                          Confidence.modelScore(0.42),
                          Map.of())));
      Translated t = run(stage, "skor", SV).translated().orElseThrow();
      assertEquals(Confidence.Kind.MODEL_SCORE, t.confidence().kind());
      assertEquals(0.42, t.confidence().value().orElseThrow());
    }
  }
}

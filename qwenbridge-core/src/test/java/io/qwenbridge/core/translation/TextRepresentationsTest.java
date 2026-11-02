package io.qwenbridge.core.translation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.translation.TextRepresentations.Transformation;
import io.qwenbridge.core.translation.TranslationResult.Failed;
import io.qwenbridge.core.translation.TranslationResult.Failure;
import io.qwenbridge.core.translation.TranslationResult.Translated;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** QB-017: original, normalized and translated text are kept apart with their derivations. */
class TextRepresentationsTest {

  static final String ORIGINAL = "ＳＫＯＲ  under ۱۲۰۰ SEK";

  @Test
  void everyFormIsKeptWithItsDerivation() {
    TranslationOutcome outcome =
        new TranslationOutcome(
            new Translated(
                "shoes under 1200 SEK",
                new LanguageTag("sv"),
                new LanguageTag("en"),
                "fixture",
                Confidence.modelDecision(),
                Map.of()),
            Optional.of(new PreservationReport(List.of(), Set.of())),
            Duration.ZERO);
    TextRepresentations text =
        TextRepresentations.of(ORIGINAL)
            .withNormalized("skor under 1200 SEK", "search-normalization-v1", List.of("nfkc"))
            .withTranslation(outcome);

    assertEquals(ORIGINAL, text.original());
    assertEquals("skor under 1200 SEK", text.normalized().orElseThrow());
    assertEquals("shoes under 1200 SEK", text.translated().orElseThrow().text());
    assertEquals(
        PreservationReport.Status.PRESERVED, text.translated().orElseThrow().preservation());
    List<Transformation> steps = text.transformations();
    assertEquals(2, steps.size());
    assertEquals(Transformation.Kind.NORMALIZATION, steps.get(0).kind());
    assertEquals("normalized", steps.get(1).from());
    assertEquals("translated", steps.get(1).to());
    assertEquals("fixture", steps.get(1).actor());
    assertEquals("sv", steps.get(1).details().get("source"));
  }

  @Test
  void aFailedTranslationLeavesTheRepresentationsUnchanged() {
    TextRepresentations text = TextRepresentations.of(ORIGINAL);
    TextRepresentations after =
        text.withTranslation(
            new TranslationOutcome(
                Failed.of(Failure.NOT_PRESERVED, "fixture"), Optional.empty(), Duration.ZERO));
    assertEquals(text, after);
    assertTrue(after.translated().isEmpty());
    assertEquals(ORIGINAL, after.translationInput());
  }

  @Test
  void normalizationCannotBeRecordedAfterTranslationOrTwice() {
    TextRepresentations text = TextRepresentations.of(ORIGINAL).withNormalized("x", "n", List.of());
    assertThrows(IllegalStateException.class, () -> text.withNormalized("y", "n", List.of()));
  }
}

package io.qwenbridge.core.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.core.confidence.Confidence;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class LanguageMetadataTest {

  @Test
  void undetectedMetadataIsHonestAboutMissingEvidence() {
    LanguageMetadata metadata = LanguageMetadata.undetected(LanguagePreferences.NONE, "میز");
    assertTrue(metadata.detected().isUndetermined());
    assertEquals(Confidence.Kind.UNKNOWN, metadata.detection().kind());
    assertTrue(metadata.detection().value().isEmpty());
    assertEquals(TextDirection.RTL, metadata.direction());
  }

  @Test
  void declaredLanguageNeverPosesAsDetectedLanguage() {
    LanguagePreferences preferences =
        new LanguagePreferences(LanguageTag.parse("sv"), Optional.empty(), Optional.empty());
    LanguageMetadata metadata = LanguageMetadata.undetected(preferences, "hello");
    assertEquals("sv", metadata.preferences().declared().orElseThrow().value());
    assertTrue(metadata.detected().isUndetermined());
  }

  @Test
  void confidenceKindsAreValidated() {
    assertEquals(0.75, Confidence.modelScore(0.75).value().orElseThrow());
    assertThrows(IllegalArgumentException.class, () -> Confidence.modelScore(1.2));
    assertThrows(IllegalArgumentException.class, () -> Confidence.modelScore(Double.NaN));
    assertThrows(
        IllegalArgumentException.class,
        () -> new Confidence(Confidence.Kind.UNKNOWN, OptionalDouble.of(0.5)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new Confidence(Confidence.Kind.MODEL_SCORE, OptionalDouble.empty()));
    assertTrue(Confidence.heuristic().value().isEmpty());
  }
}

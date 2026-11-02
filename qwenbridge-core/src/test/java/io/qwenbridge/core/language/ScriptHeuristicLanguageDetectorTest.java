package io.qwenbridge.core.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.request.LanguageTag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScriptHeuristicLanguageDetectorTest {

  private final ScriptHeuristicLanguageDetector detector = new ScriptHeuristicLanguageDetector();

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource(
      delimiter = '|',
      value = {
        "안녕하세요 | ko",
        "東京のホテル | ja",
        "נעלי ריצה | he",
        "کفش مشکی | fa",
        "یہ کتاب ے | ur",
        "Γειά σου | el"
      })
  void singleLanguageScriptsAreIdentifiedHeuristically(String text, String expected) {
    LanguageDetection detection = detector.detect(text);
    assertEquals(expected, detection.language().value());
    assertEquals(Confidence.Kind.HEURISTIC, detection.confidence().kind());
    assertEquals(ScriptHeuristicLanguageDetector.ID, detection.detector());
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "hello world",
        "laptop",
        "svarta skor",
        "ما هي أفضل",
        "東京",
        "Привет",
        "12345 !!! ???",
        "Nike shoes برای winter"
      })
  void sharedScriptsRemainUndetermined(String text) {
    LanguageDetection detection = detector.detect(text);
    assertEquals(LanguageTag.UNDETERMINED, detection.language());
    assertEquals(Confidence.Kind.UNKNOWN, detection.confidence().kind());
    assertFalse(detection.determined());
    assertEquals("unknown", detection.legacyCode());
  }

  @org.junit.jupiter.api.Test
  void undeterminedDetectionCannotClaimConfidence() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new LanguageDetection(LanguageTag.UNDETERMINED, Confidence.modelScore(0.9), "x"));
  }
}

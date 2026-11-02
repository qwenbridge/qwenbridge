package io.qwenbridge.core.language;

import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.request.LanguageTag;
import java.util.Objects;

/**
 * Result of language detection (QB-008). {@link LanguageTag#UNDETERMINED} together with {@link
 * Confidence.Kind#UNKNOWN} means the evidence was insufficient; detectors must return that instead
 * of guessing.
 *
 * @param language detected language, or {@code und}
 * @param confidence how the decision was made and, for model scores only, the score
 * @param detector identifier of the detector that produced the decision
 */
public record LanguageDetection(LanguageTag language, Confidence confidence, String detector) {

  public LanguageDetection {
    Objects.requireNonNull(language, "language must not be null");
    Objects.requireNonNull(confidence, "confidence must not be null");
    Objects.requireNonNull(detector, "detector must not be null");
    if (language.isUndetermined() && confidence.kind() != Confidence.Kind.UNKNOWN) {
      throw new IllegalArgumentException("An undetermined language cannot carry a confidence");
    }
  }

  public static LanguageDetection undetermined(String detector) {
    return new LanguageDetection(LanguageTag.UNDETERMINED, Confidence.unknown(), detector);
  }

  public boolean determined() {
    return !language.isUndetermined();
  }

  /** Legacy single-string form used by v1 responses: the primary subtag or {@code unknown}. */
  public String legacyCode() {
    return determined() ? language.primaryLanguage() : "unknown";
  }
}

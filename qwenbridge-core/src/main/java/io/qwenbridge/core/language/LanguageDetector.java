package io.qwenbridge.core.language;

/**
 * Replaceable language detection SPI (QB-008). Implementations may be rule-based, statistical or
 * model-based. They must:
 *
 * <ul>
 *   <li>never throw for ordinary text (return {@link LanguageDetection#undetermined} instead);
 *   <li>return {@code und} when evidence is insufficient rather than defaulting to any language;
 *   <li>report model scores only as {@code MODEL_SCORE}, never as calibrated probabilities.
 * </ul>
 */
public interface LanguageDetector {

  /** Stable identifier recorded in detection results and traces. */
  String id();

  LanguageDetection detect(String text);
}

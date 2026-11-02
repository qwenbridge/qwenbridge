package io.qwenbridge.core.confidence;

/** The parts of understanding that report their own confidence (QB-078). */
public enum ConfidenceComponent {
  LANGUAGE,
  INTENT,
  EXTRACTION,
  TRANSLATION,
  THREAT,
  /** A model's intent classification score. */
  MODEL_INTENT,
  /** A model's semantic-validation score. */
  MODEL_SEMANTIC,
  /** A model's planning-decision score. */
  MODEL_DECISION
}

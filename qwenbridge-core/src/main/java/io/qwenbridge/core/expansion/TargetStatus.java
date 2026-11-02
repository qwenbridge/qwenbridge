package io.qwenbridge.core.expansion;

/** The outcome for one configured target language (QB-286). */
public enum TargetStatus {
  /** Translated, and the translation kept every checked meaning and identifier. */
  SUCCESS,
  /** The query is already in this language; the normalized source query is the variant. */
  UNCHANGED,
  /** Translated, but the provider's score or coverage is below the configured minimum. */
  LOW_CONFIDENCE,
  /** No installed provider produces this language. */
  UNSUPPORTED_LANGUAGE,
  /** A provider produces this language, but not from the query's language. */
  UNSUPPORTED_LANGUAGE_PAIR,
  /** A capable provider exists but is not available (no provider, missing model or resources). */
  PROVIDER_UNAVAILABLE,
  /** The source language could not be determined and no capable provider detects languages. */
  SOURCE_UNDETERMINED,
  /** The configured identifier is invalid or ambiguous; nothing was attempted. */
  INVALID_LANGUAGE,
  /** Attempted and failed (timeout, provider error, invalid output, meaning not preserved). */
  FAILED;

  /** Whether the variant is used for retrieval under the policy. */
  public boolean usable(ExpansionPolicy policy) {
    return this == SUCCESS
        || this == UNCHANGED
        || (this == LOW_CONFIDENCE && policy == ExpansionPolicy.BEST_EFFORT_ALL_TARGETS);
  }

  public boolean succeeded() {
    return this == SUCCESS || this == UNCHANGED;
  }
}

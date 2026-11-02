package io.qwenbridge.core.spelling;

/**
 * Typo correction policy (QB-291). Configured explicitly by the application owner; the engine never
 * escalates on its own.
 */
public enum CorrectionMode {
  /** No correction work at all; the query is passed through. */
  DISABLED,
  /** Candidates are computed and reported; the query is never changed. */
  SUGGEST_ONLY,
  /** Only high-confidence, single-edit (or two-edit for long words) corrections are applied. */
  SAFE_AUTO_CORRECT,
  /** Lower thresholds and up to two edits; still never touches protected tokens. */
  AGGRESSIVE;

  public boolean applies() {
    return this == SAFE_AUTO_CORRECT || this == AGGRESSIVE;
  }
}

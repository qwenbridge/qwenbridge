package io.qwenbridge.core.expansion;

/**
 * How the per-language results combine (QB-286). Every policy attempts every configured target
 * language and reports a result for each; none reduces the configured list.
 */
public enum ExpansionPolicy {
  /**
   * The default. Every target is attempted; {@code SUCCESS} and {@code UNCHANGED} variants are used
   * for retrieval, failed and low-confidence ones are reported but not used.
   */
  ALL_TARGETS,
  /**
   * The expansion fails unless every target is {@code SUCCESS} or {@code UNCHANGED}; on failure no
   * variant is used for retrieval (only the source query). Per-language results are still reported.
   */
  STRICT_ALL_TARGETS,
  /**
   * Like {@code ALL_TARGETS}, but {@code LOW_CONFIDENCE} variants are also used for retrieval
   * (flagged as such).
   */
  BEST_EFFORT_ALL_TARGETS
}

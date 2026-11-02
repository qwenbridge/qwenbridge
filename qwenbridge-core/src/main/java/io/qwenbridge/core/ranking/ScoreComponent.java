package io.qwenbridge.core.ranking;

import java.util.Objects;

/**
 * One named part of a ranking score (QB-123). {@code raw} is what the source produced, in its own
 * units; {@code contribution} is what it added to the baseline score under the policy. Raw scores
 * of different kinds are never added together: only contributions are, and they are documented per
 * kind.
 *
 * <ul>
 *   <li>RETRIEVAL: raw = the provider's rank (1 = best); contribution = weight × (n − rank + 1) /
 *       n, an ordinal value in (0, weight].
 *   <li>PREFERENCE: raw = preferences satisfied; contribution = weight × satisfied / total.
 *   <li>BUSINESS_RULE: raw = the rule's boost in [−1, 1]; the sum of matched boosts is clamped to
 *       [−1, 1] before the weight is applied.
 *   <li>RERANK: raw = the reranker's position (1 = best) or its score; contribution 0. A reranker
 *       decides order within its window; it does not change the baseline score.
 * </ul>
 */
public record ScoreComponent(
    String name, Kind kind, double raw, double contribution, String source) {
  public enum Kind {
    RETRIEVAL,
    PREFERENCE,
    BUSINESS_RULE,
    RERANK
  }

  public ScoreComponent {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(source, "source must not be null");
    if (!Double.isFinite(raw) || !Double.isFinite(contribution)) {
      throw new IllegalArgumentException("score components must be finite");
    }
  }
}

package io.qwenbridge.core.ranking;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * A versioned, configurable ranking policy (QB-117). Every ranking states the policy id and version
 * it used, so a result order can be reproduced and a policy change is visible.
 *
 * <ul>
 *   <li>Weights are finite and non-negative; retrieval relevance always counts ({@code
 *       retrievalWeight > 0}).
 *   <li>Business rules are subordinate to relevance: {@code businessWeight <= retrievalWeight}, so
 *       a rule alone can never outweigh the full retrieval signal.
 *   <li>{@code rerankWindow} is how many top results a reranker may reorder (0 disables it).
 * </ul>
 */
public record RankingPolicy(
    String id,
    String version,
    double retrievalWeight,
    double preferenceWeight,
    double businessWeight,
    List<BusinessRule> rules,
    int rerankWindow) {

  public static final RankingPolicy DEFAULT =
      new RankingPolicy("qwenbridge-baseline", "1.0", 1.0, 0.5, 0.0, List.of(), 20);

  public RankingPolicy {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(version, "version must not be null");
    if (!id.matches("[a-z0-9][a-z0-9._-]{0,63}") || !version.matches("[0-9]+(\\.[0-9]+){0,2}")) {
      throw new IllegalArgumentException("policy id or version invalid");
    }
    rules = rules == null ? List.of() : List.copyOf(rules);
    for (double w : new double[] {retrievalWeight, preferenceWeight, businessWeight}) {
      if (!Double.isFinite(w) || w < 0 || w > 10) {
        throw new IllegalArgumentException("weights must be within [0, 10]");
      }
    }
    if (retrievalWeight <= 0) {
      throw new IllegalArgumentException("retrieval relevance must count");
    }
    if (businessWeight > retrievalWeight) {
      throw new IllegalArgumentException("business rules may not outweigh retrieval relevance");
    }
    if (rules.size() > 64
        || new HashSet<>(rules.stream().map(BusinessRule::id).toList()).size() != rules.size()) {
      throw new IllegalArgumentException("at most 64 business rules with unique ids");
    }
    if (!rules.isEmpty() && businessWeight == 0) {
      throw new IllegalArgumentException("business rules need a business weight");
    }
    if (rerankWindow < 0 || rerankWindow > 100) {
      throw new IllegalArgumentException("rerankWindow must be within [0, 100]");
    }
  }

  /** {@code id@version}, as reported with every ranking. */
  public String key() {
    return id + "@" + version;
  }
}

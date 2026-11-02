package io.qwenbridge.core.ranking;

import io.qwenbridge.core.retrieval.filter.Filter;
import io.qwenbridge.core.retrieval.filter.FilterEvaluator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;

/**
 * The deterministic baseline ranking (QB-118). It needs no model and no network, so every path can
 * apply it. The score is the sum of three documented contributions ({@link ScoreComponent}):
 * retrieval rank, satisfied SOFT preferences and matched business rules. Ties break by retrieval
 * rank, then by id, so the order never depends on the input order.
 */
public final class BaselineRanker {

  private BaselineRanker() {}

  /**
   * @param preferences SOFT preferences as filters; evaluated per result, never used to exclude
   */
  public static List<RankedItem> rank(
      RankingPolicy policy, List<RankCandidate> candidates, List<Filter> preferences) {
    if (new HashSet<>(candidates.stream().map(RankCandidate::id).toList()).size()
        != candidates.size()) {
      throw new IllegalArgumentException("candidate ids must be unique");
    }
    int n = candidates.size();
    List<RankedItem> scored = new ArrayList<>(n);
    for (RankCandidate c : candidates) {
      List<ScoreComponent> parts = new ArrayList<>();
      int rank = Math.min(c.retrievalRank(), n);
      double retrieval = policy.retrievalWeight() * (n - rank + 1) / (double) n;
      parts.add(
          new ScoreComponent(
              "retrieval",
              ScoreComponent.Kind.RETRIEVAL,
              c.retrievalRank(),
              retrieval,
              c.retrievalScore().scorer()));
      double score = retrieval;
      if (!preferences.isEmpty() && policy.preferenceWeight() > 0) {
        long satisfied =
            preferences.stream().filter(p -> FilterEvaluator.matches(p, c.fields())).count();
        double contribution = policy.preferenceWeight() * satisfied / preferences.size();
        parts.add(
            new ScoreComponent(
                "preferences",
                ScoreComponent.Kind.PREFERENCE,
                satisfied,
                contribution,
                policy.key()));
        score += contribution;
      }
      double boost = 0;
      for (BusinessRule rule : policy.rules()) {
        if (FilterEvaluator.matches(rule.condition(), c.fields())) {
          boost += rule.boost();
          parts.add(
              new ScoreComponent(
                  "rule:" + rule.id(),
                  ScoreComponent.Kind.BUSINESS_RULE,
                  rule.boost(),
                  0,
                  policy.key()));
        }
      }
      if (boost != 0) {
        double contribution = policy.businessWeight() * Math.clamp(boost, -1.0, 1.0);
        parts.add(
            new ScoreComponent(
                "business", ScoreComponent.Kind.BUSINESS_RULE, boost, contribution, policy.key()));
        score += contribution;
      }
      scored.add(
          new RankedItem(
              c.id(),
              0,
              score,
              c.retrievalRank(),
              c.retrievalScore(),
              parts,
              c.fields(),
              OptionalInt.empty()));
    }
    scored.sort(
        Comparator.comparingDouble(RankedItem::score)
            .reversed()
            .thenComparingInt(RankedItem::retrievalRank)
            .thenComparing(RankedItem::id));
    List<RankedItem> out = new ArrayList<>(n);
    for (int i = 0; i < scored.size(); i++) {
      out.add(scored.get(i).at(i + 1));
    }
    return List.copyOf(out);
  }
}

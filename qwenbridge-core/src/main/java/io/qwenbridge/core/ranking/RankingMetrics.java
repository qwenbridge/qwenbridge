package io.qwenbridge.core.ranking;

import java.util.List;
import java.util.Map;

/**
 * Ranking quality against graded relevance judgments (QB-124). Grades are non-negative integers; 0
 * means not relevant. Missing ids count as grade 0.
 */
public final class RankingMetrics {

  private RankingMetrics() {}

  /**
   * Normalized discounted cumulative gain at k with gain 2^grade − 1. 0 when nothing is relevant.
   */
  public static double ndcg(List<String> ranked, Map<String, Integer> grades, int k) {
    double dcg = dcg(ranked.stream().map(id -> grades.getOrDefault(id, 0)).toList(), k);
    double ideal =
        dcg(grades.values().stream().sorted((a, b) -> Integer.compare(b, a)).toList(), k);
    return ideal == 0 ? 0 : dcg / ideal;
  }

  /** Reciprocal rank of the first item with a grade above 0. */
  public static double reciprocalRank(List<String> ranked, Map<String, Integer> grades) {
    for (int i = 0; i < ranked.size(); i++) {
      if (grades.getOrDefault(ranked.get(i), 0) > 0) {
        return 1.0 / (i + 1);
      }
    }
    return 0;
  }

  /** Share of the first k positions holding an item with a grade above 0. */
  public static double precision(List<String> ranked, Map<String, Integer> grades, int k) {
    if (k < 1) {
      throw new IllegalArgumentException("k must be positive");
    }
    long hits = ranked.stream().limit(k).filter(id -> grades.getOrDefault(id, 0) > 0).count();
    return hits / (double) k;
  }

  private static double dcg(List<Integer> grades, int k) {
    double sum = 0;
    for (int i = 0; i < Math.min(k, grades.size()); i++) {
      int g = grades.get(i);
      if (g < 0) {
        throw new IllegalArgumentException("grades are non-negative");
      }
      sum += (Math.pow(2, g) - 1) / (Math.log(i + 2) / Math.log(2));
    }
    return sum;
  }
}

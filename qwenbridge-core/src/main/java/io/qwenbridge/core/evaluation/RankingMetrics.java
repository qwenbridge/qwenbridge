package io.qwenbridge.core.evaluation;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ranked-retrieval quality metrics over graded relevance judgments (QB-209).
 *
 * <p>Judgments map document ids to grades: 0 = not relevant (also every unjudged document), 1 =
 * partially, 2 = relevant, 3 = the exact answer. A document is <em>relevant</em> when its grade is
 * at least 1.
 *
 * <ul>
 *   <li><b>Precision@K</b>: relevant documents among the first K positions, divided by K (a short
 *       list is not rewarded: missing positions count as non-relevant).
 *   <li><b>Recall@K</b>: relevant documents among the first K, divided by all relevant documents.
 *   <li><b>Reciprocal rank</b>: 1 / position of the first relevant document, 0 if none is returned.
 *       Its mean over queries is MRR.
 *   <li><b>nDCG@K</b>: DCG@K = Σ (2^grade − 1) / log2(position + 1) over the first K positions,
 *       divided by the DCG of the ideal ordering of all judged documents. 1 means the best possible
 *       order; grades make an exact answer above a partial one count.
 * </ul>
 *
 * Recall, reciprocal rank and nDCG are undefined for a query without relevant documents; such
 * queries are evaluated separately (see {@link #falsePositives}).
 */
public final class RankingMetrics {

  private RankingMetrics() {}

  static int grade(Map<String, Integer> judgments, String id) {
    Integer g = judgments.get(id);
    return g == null ? 0 : g;
  }

  public static long relevantCount(Map<String, Integer> judgments) {
    return judgments.values().stream().filter(g -> g >= 1).count();
  }

  public static double precisionAtK(Map<String, Integer> judgments, List<String> ranked, int k) {
    requireK(k);
    long hits = ranked.stream().limit(k).filter(id -> grade(judgments, id) >= 1).count();
    return hits / (double) k;
  }

  public static double recallAtK(Map<String, Integer> judgments, List<String> ranked, int k) {
    requireK(k);
    long relevant = relevantCount(judgments);
    if (relevant == 0)
      throw new IllegalArgumentException("recall is undefined without relevant documents");
    long hits = ranked.stream().limit(k).filter(id -> grade(judgments, id) >= 1).count();
    return hits / (double) relevant;
  }

  public static double reciprocalRank(Map<String, Integer> judgments, List<String> ranked) {
    if (relevantCount(judgments) == 0)
      throw new IllegalArgumentException("reciprocal rank is undefined without relevant documents");
    for (int i = 0; i < ranked.size(); i++)
      if (grade(judgments, ranked.get(i)) >= 1) return 1.0 / (i + 1);
    return 0.0;
  }

  public static double ndcgAtK(Map<String, Integer> judgments, List<String> ranked, int k) {
    requireK(k);
    double ideal = idealDcg(judgments, k);
    if (ideal == 0)
      throw new IllegalArgumentException("nDCG is undefined without relevant documents");
    double dcg = 0;
    for (int i = 0; i < Math.min(k, ranked.size()); i++)
      dcg += gain(grade(judgments, ranked.get(i)), i);
    return dcg / ideal;
  }

  /** For a query with no relevant document: how many results were returned in the first K. */
  public static int falsePositives(List<String> ranked, int k) {
    requireK(k);
    return Math.min(k, ranked.size());
  }

  static double idealDcg(Map<String, Integer> judgments, int k) {
    var grades =
        judgments.values().stream().filter(g -> g >= 1).sorted((a, b) -> b - a).limit(k).toList();
    double ideal = 0;
    for (int i = 0; i < grades.size(); i++) ideal += gain(grades.get(i), i);
    return ideal;
  }

  private static double gain(int grade, int index) {
    return (Math.pow(2, grade) - 1) / (Math.log(index + 2) / Math.log(2));
  }

  private static void requireK(int k) {
    if (k < 1) throw new IllegalArgumentException("k must be at least 1");
  }

  /** Validates a judgment map: ids non-blank, grades 0..3. */
  public static Map<String, Integer> judgments(Map<String, Integer> raw) {
    Objects.requireNonNull(raw, "judgments must not be null");
    raw.forEach(
        (id, g) -> {
          if (id == null || id.isBlank() || g == null || g < 0 || g > 3)
            throw new IllegalArgumentException("Invalid judgment " + id + "=" + g);
        });
    return Map.copyOf(raw);
  }
}

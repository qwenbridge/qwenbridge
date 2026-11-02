package io.qwenbridge.core.ranking;

import static org.junit.jupiter.api.Assertions.*;

import io.qwenbridge.core.confidence.RetrievalScore;
import io.qwenbridge.core.retrieval.filter.FieldValue;
import io.qwenbridge.core.retrieval.filter.Filter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * QB-124: ranking quality against graded relevance judgments on a synthetic seed set. The judgments
 * are written for this test (contract-level evidence, audit H7); they show the ranking does what
 * its policy says and stays deterministic, not that it is good on real traffic.
 *
 * <p>Each query has retrieved candidates in retrieval order, the user's SOFT preferences, and
 * graded judgments (3 = exactly what was asked, 2 = good, 1 = acceptable, 0 = wrong).
 */
class RankingJudgmentsTest {

  record Query(
      String id,
      List<RankCandidate> retrieved,
      List<Filter> preferences,
      Map<String, Integer> grades) {}

  static RankCandidate c(String id, int rank, Object... kv) {
    var fields = new java.util.HashMap<String, FieldValue>();
    for (int i = 0; i < kv.length; i += 2)
      fields.put(
          (String) kv[i],
          kv[i + 1] instanceof Boolean b
              ? new FieldValue.Bool(b)
              : FieldValue.text((String) kv[i + 1]));
    return new RankCandidate(
        id, rank, new RetrievalScore(1.0 / rank, RetrievalScore.Kind.BM25, "kw"), fields);
  }

  static Filter eq(String field, String value) {
    return new Filter.Compare(field, Filter.Compare.Op.EQ, FieldValue.text(value));
  }

  static final List<Query> QUERIES =
      List.of(
          // "running shoes, preferably black": retrieval ranks by text only.
          new Query(
              "q1",
              List.of(
                  c("r1", 1, "color", "white"),
                  c("r2", 2, "color", "red"),
                  c("r3", 3, "color", "black"),
                  c("r4", 4, "color", "black"),
                  c("r5", 5, "color", "blue")),
              List.of(eq("color", "black")),
              Map.of("r3", 3, "r4", 3, "r1", 1, "r2", 1, "r5", 1)),
          // "laptop, ideally in stock and refurbished"
          new Query(
              "q2",
              List.of(
                  c("l1", 1, "condition", "new", "inStock", false),
                  c("l2", 2, "condition", "refurbished", "inStock", true),
                  c("l3", 3, "condition", "refurbished", "inStock", false),
                  c("l4", 4, "condition", "new", "inStock", true)),
              List.of(
                  eq("condition", "refurbished"),
                  new Filter.Compare("inStock", Filter.Compare.Op.EQ, new FieldValue.Bool(true))),
              Map.of("l2", 3, "l3", 2, "l4", 2, "l1", 0)),
          // No preferences: the baseline must keep the retrieval order exactly.
          new Query(
              "q3",
              List.of(c("h1", 1), c("h2", 2), c("h3", 3)),
              List.of(),
              Map.of("h1", 3, "h2", 2, "h3", 1)));

  static double mean(List<Double> values) {
    return values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
  }

  @Test
  void baselineWithPreferencesBeatsRetrievalOrderOnTheJudgments() {
    List<Double> retrievalOnly = new ArrayList<>();
    List<Double> baseline = new ArrayList<>();
    for (Query q : QUERIES) {
      List<String> retrievalOrder = q.retrieved().stream().map(RankCandidate::id).toList();
      List<String> ranked =
          BaselineRanker.rank(RankingPolicy.DEFAULT, q.retrieved(), q.preferences()).stream()
              .map(RankedItem::id)
              .toList();
      retrievalOnly.add(RankingMetrics.ndcg(retrievalOrder, q.grades(), 3));
      baseline.add(RankingMetrics.ndcg(ranked, q.grades(), 3));
    }
    // Exact, reproducible values for this seed set (deterministic ranking).
    assertEquals(
        // r3: 0.6 + 0.5 = 1.1; r1: 1.0; r4: 0.4 + 0.5 = 0.9; r2: 0.8; r5: 0.2.
        List.of("r3", "r1", "r4", "r2", "r5"),
        BaselineRanker.rank(
                RankingPolicy.DEFAULT, QUERIES.get(0).retrieved(), QUERIES.get(0).preferences())
            .stream()
            .map(RankedItem::id)
            .toList());
    assertEquals(1.0, baseline.get(2), 1e-12, "no preferences: retrieval order kept");
    assertEquals(retrievalOnly.get(2), baseline.get(2), 1e-12);
    assertTrue(mean(baseline) > mean(retrievalOnly), baseline + " vs " + retrievalOnly);
    // q1: nDCG@3 0.934 against 0.431 for the retrieval order; q2 improves as well.
    assertEquals(0.934, baseline.get(0), 0.001);
    assertEquals(0.431, retrievalOnly.get(0), 0.001);
    assertTrue(baseline.get(1) > retrievalOnly.get(1));
  }

  @Test
  void repeatedRunsGiveIdenticalRankings() {
    for (Query q : QUERIES) {
      var first = BaselineRanker.rank(RankingPolicy.DEFAULT, q.retrieved(), q.preferences());
      for (int i = 0; i < 50; i++)
        assertEquals(
            first, BaselineRanker.rank(RankingPolicy.DEFAULT, q.retrieved(), q.preferences()));
    }
  }
}

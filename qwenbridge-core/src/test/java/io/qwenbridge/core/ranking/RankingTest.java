package io.qwenbridge.core.ranking;

import static org.junit.jupiter.api.Assertions.*;

import io.qwenbridge.core.confidence.RetrievalScore;
import io.qwenbridge.core.retrieval.filter.FieldValue;
import io.qwenbridge.core.retrieval.filter.Filter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** QB-117 – QB-123: policy, baseline, reranker SPI, business rules and re-validation. */
class RankingTest {

  static RankCandidate c(String id, int rank, Map<String, FieldValue> fields) {
    return new RankCandidate(
        id, rank, new RetrievalScore(10.0 / rank, RetrievalScore.Kind.BM25, "kw"), fields);
  }

  static Map<String, FieldValue> f(String brand, boolean inStock) {
    return Map.of("brand", FieldValue.text(brand), "inStock", new FieldValue.Bool(inStock));
  }

  static List<String> ids(List<RankedItem> items) {
    return items.stream().map(RankedItem::id).toList();
  }

  static final Filter IN_STOCK =
      new Filter.Compare("inStock", Filter.Compare.Op.EQ, new FieldValue.Bool(true));

  static List<RankCandidate> five() {
    return List.of(
        c("a", 1, f("Nike", false)),
        c("b", 2, f("Nike", true)),
        c("c", 3, f("Adidas", true)),
        c("d", 4, f("Puma", false)),
        c("e", 5, f("Nike", true)));
  }

  @Test
  void policiesAreVersionedAndBounded() {
    assertEquals("qwenbridge-baseline@1.0", RankingPolicy.DEFAULT.key());
    var rule = new BusinessRule("promote-stock", IN_STOCK, 0.5);
    assertThrows(
        IllegalArgumentException.class,
        () -> new RankingPolicy("p", "1", 1, 0, 2, List.of(rule), 10),
        "business rules may not outweigh relevance");
    assertThrows(
        IllegalArgumentException.class, () -> new RankingPolicy("p", "1", 0, 1, 0, List.of(), 10));
    assertThrows(
        IllegalArgumentException.class,
        () -> new RankingPolicy("p", "1", 1, 0, 1, List.of(rule, rule), 10),
        "duplicate rule ids");
    assertThrows(
        IllegalArgumentException.class,
        () -> new RankingPolicy("p", "1", 1, 0, 0, List.of(rule), 10));
    assertThrows(
        IllegalArgumentException.class, () -> new RankingPolicy("P!", "1", 1, 0, 0, List.of(), 10));
    assertThrows(
        IllegalArgumentException.class, () -> new RankingPolicy("p", "", 1, 0, 0, List.of(), 10));
    assertThrows(IllegalArgumentException.class, () -> new BusinessRule("r", IN_STOCK, 1.5));
    assertThrows(IllegalArgumentException.class, () -> new BusinessRule("r", IN_STOCK, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new RankingPolicy("p", "1", Double.NaN, 0, 0, List.of(), 10));
  }

  @Test
  void baselineIsDeterministicWhateverTheInputOrder() {
    var policy = RankingPolicy.DEFAULT;
    List<String> expected = ids(BaselineRanker.rank(policy, five(), List.of(IN_STOCK)));
    var shuffled = new ArrayList<>(five());
    for (long seed = 0; seed < 20; seed++) {
      Collections.shuffle(shuffled, new Random(seed));
      assertEquals(expected, ids(BaselineRanker.rank(policy, shuffled, List.of(IN_STOCK))));
    }
    // Equal scores break by retrieval rank, then id.
    var tie =
        BaselineRanker.rank(policy, List.of(c("z", 1, Map.of()), c("y", 1, Map.of())), List.of());
    assertEquals(List.of("y", "z"), ids(tie));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            BaselineRanker.rank(
                policy, List.of(c("a", 1, Map.of()), c("a", 2, Map.of())), List.of()));
  }

  @Test
  void scoresAreDocumentedComponentsAndRetrievalScoresSurvive() {
    var ranked = BaselineRanker.rank(RankingPolicy.DEFAULT, five(), List.of(IN_STOCK));
    // b: 1.0 × (5−2+1)/5 + 0.5 = 1.3; c: 0.6 + 0.5 = 1.1; a: 1.0 (out of stock);
    // e: 0.2 + 0.5 = 0.7; d: 0.4.
    assertEquals(List.of("b", "c", "a", "e", "d"), ids(ranked));
    RankedItem b = ranked.getFirst();
    assertEquals(1.3, b.score(), 1e-9);
    assertEquals(
        b.score(), b.components().stream().mapToDouble(ScoreComponent::contribution).sum(), 1e-9);
    assertEquals(5.0, b.retrievalScore().value(), 1e-9);
    assertEquals(RetrievalScore.Kind.BM25, b.retrievalScore().kind());
    assertEquals(2, b.retrievalRank());
  }

  @Test
  void businessRulesOnlyReorderAndStayBounded() {
    var demote =
        new BusinessRule(
            "demote-nike",
            new Filter.Compare("brand", Filter.Compare.Op.EQ, FieldValue.text("Nike")),
            -1);
    var promote =
        new BusinessRule(
            "promote-puma",
            new Filter.Compare("brand", Filter.Compare.Op.EQ, FieldValue.text("Puma")),
            1);
    var policy = new RankingPolicy("shop", "2.1", 1, 0, 0.5, List.of(demote, promote), 0);
    var ranked = BaselineRanker.rank(policy, five(), List.of());
    // Same set, different order: rules never add or remove.
    assertEquals(
        five().stream().map(RankCandidate::id).sorted().toList(),
        ids(ranked).stream().sorted().toList());
    assertEquals("d", ranked.getFirst().id());
    assertTrue(
        ranked.getFirst().components().stream()
            .anyMatch(p -> p.name().equals("rule:promote-puma")));
    // A boosted item that violates a HARD constraint is still removed by re-validation.
    var hard = new Filter.Compare("inStock", Filter.Compare.Op.EQ, new FieldValue.Bool(true));
    var result =
        RankingPipeline.run(
            policy, five(), List.of(), hard, Optional.empty(), "q", Duration.ofSeconds(1));
    assertEquals(2, result.removedByValidation());
    assertFalse(ids(result.items()).contains("d"));
    assertEquals("shop@2.1", result.policy());
    for (int i = 0; i < result.items().size(); i++)
      assertEquals(i + 1, result.items().get(i).position());
  }

  static Reranker reranker(java.util.function.Function<List<Reranker.Item>, Reranker.Response> f) {
    return new Reranker() {
      public String id() {
        return "test-reranker";
      }

      public Reranker.Response rerank(String query, List<Reranker.Item> items) {
        return f.apply(items);
      }
    };
  }

  @Test
  void rerankingReordersTheWindowAndPreservesScores() {
    var policy = new RankingPolicy("p", "1", 1, 0, 0, List.of(), 3);
    var reverse =
        reranker(
            items ->
                new Reranker.Response(
                    items.stream().map(Reranker.Item::id).toList().reversed(),
                    Optional.of(List.of(0.9, 0.5, 0.1))));
    var result =
        RankingPipeline.run(
            policy,
            five(),
            List.of(),
            Filter.ALL,
            Optional.of(reverse),
            "q",
            Duration.ofSeconds(2));
    assertEquals(RerankStage.Status.APPLIED, result.rerank());
    assertEquals(List.of("c", "b", "a", "d", "e"), ids(result.items()));
    RankedItem c = result.items().getFirst();
    assertEquals(1.0 * (5 - 3 + 1) / 5, c.score(), 1e-9, "baseline score preserved");
    assertEquals(10.0 / 3, c.retrievalScore().value(), 1e-9, "retrieval score preserved");
    assertEquals(1, c.rerankPosition().orElseThrow());
    assertTrue(
        c.components().stream()
            .anyMatch(p -> p.kind() == ScoreComponent.Kind.RERANK && p.raw() == 0.9));
    assertTrue(result.items().get(3).rerankPosition().isEmpty(), "outside the window");
  }

  @Test
  void misbehavingRerankersNeverChangeTheResultSet() {
    var policy = new RankingPolicy("p", "1", 1, 0, 0, List.of(), 5);
    List<String> baseline = ids(BaselineRanker.rank(policy, five(), List.of()));
    record Case(Reranker reranker, RerankStage.Status status) {}
    var cases =
        List.of(
            new Case(
                reranker(items -> Reranker.Response.of(List.of("a", "b"))),
                RerankStage.Status.INVALID_PERMUTATION),
            new Case(
                reranker(items -> Reranker.Response.of(List.of("a", "a", "b", "c", "d"))),
                RerankStage.Status.INVALID_PERMUTATION),
            new Case(
                reranker(items -> Reranker.Response.of(List.of("a", "b", "c", "d", "x"))),
                RerankStage.Status.INVALID_PERMUTATION),
            new Case(
                reranker(
                    items ->
                        new Reranker.Response(
                            items.stream().map(Reranker.Item::id).toList(),
                            Optional.of(List.of(1.0)))),
                RerankStage.Status.INVALID_PERMUTATION),
            new Case(
                reranker(
                    items -> {
                      throw new IllegalStateException("down");
                    }),
                RerankStage.Status.FAILED),
            new Case(reranker(items -> null), RerankStage.Status.INVALID_PERMUTATION),
            new Case(
                reranker(
                    items -> {
                      try {
                        Thread.sleep(2_000);
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                      }
                      return Reranker.Response.of(items.stream().map(Reranker.Item::id).toList());
                    }),
                RerankStage.Status.TIMEOUT));
    for (Case k : cases) {
      var result =
          RankingPipeline.run(
              policy,
              five(),
              List.of(),
              Filter.ALL,
              Optional.of(k.reranker()),
              "q",
              Duration.ofMillis(200));
      assertEquals(k.status(), result.rerank());
      assertEquals(baseline, ids(result.items()));
      assertEquals(0, result.removedByValidation());
    }
    assertEquals(
        RerankStage.Status.DISABLED,
        RankingPipeline.run(
                policy, five(), List.of(), Filter.ALL, Optional.empty(), "q", Duration.ofSeconds(1))
            .rerank());
    var seen = new AtomicReference<List<Reranker.Item>>();
    RankingPipeline.run(
        policy,
        five(),
        List.of(),
        Filter.ALL,
        Optional.of(
            reranker(
                items -> {
                  seen.set(items);
                  return Reranker.Response.of(items.stream().map(Reranker.Item::id).toList());
                })),
        "q",
        Duration.ofSeconds(1));
    assertEquals(5, seen.get().size(), "the reranker sees ids and fields only");
  }

  @Test
  void metricsMatchHandComputedValues() {
    var grades = Map.of("a", 3, "b", 2, "c", 0, "d", 1);
    // DCG = (2^0−1)/1 + (2^3−1)/log2(3) + (2^2−1)/2 = 0 + 4.41650 + 1.5
    // IDCG = 7/1 + 3/log2(3) + 1/2 = 7 + 1.89279 + 0.5
    double expected =
        (7 / (Math.log(3) / Math.log(2)) + 1.5) / (7 + 3 / (Math.log(3) / Math.log(2)) + 0.5);
    assertEquals(expected, RankingMetrics.ndcg(List.of("c", "a", "b"), grades, 3), 1e-12);
    assertEquals(1.0, RankingMetrics.ndcg(List.of("a", "b", "d"), grades, 3), 1e-12);
    assertEquals(0.5, RankingMetrics.reciprocalRank(List.of("c", "a"), grades));
    assertEquals(2 / 3.0, RankingMetrics.precision(List.of("c", "a", "b"), grades, 3), 1e-12);
    assertEquals(0, RankingMetrics.ndcg(List.of("x"), Map.of("x", 0), 3));
  }
}

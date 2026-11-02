package io.qwenbridge.core.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** QB-209: metric values checked against hand calculations. */
class RankingMetricsTest {

  static final Map<String, Integer> J = Map.of("a", 3, "b", 2, "c", 1, "x", 0);
  static final double E = 1e-9;

  @Test
  void precisionRecallAndReciprocalRank() {
    var ranked = List.of("x", "b", "y", "a");
    assertEquals(0.5, RankingMetrics.precisionAtK(J, ranked, 4), E);
    assertEquals(1.0 / 3, RankingMetrics.precisionAtK(J, ranked, 3), E);
    assertEquals(
        0.2,
        RankingMetrics.precisionAtK(J, List.of("a"), 5),
        E,
        "missing positions are not rewarded");
    assertEquals(2.0 / 3, RankingMetrics.recallAtK(J, ranked, 4), E);
    assertEquals(1.0 / 3, RankingMetrics.recallAtK(J, ranked, 2), E);
    assertEquals(0.5, RankingMetrics.reciprocalRank(J, ranked), E);
    assertEquals(0.0, RankingMetrics.reciprocalRank(J, List.of("x", "y")), E);
  }

  @Test
  void ndcgUsesGradedGainsAndTheIdealOrder() {
    // Ideal: a(3), b(2), c(1): 7/1 + 3/log2(3) + 1/2
    double ideal = 7 + 3 / (Math.log(3) / Math.log(2)) + 0.5;
    assertEquals(1.0, RankingMetrics.ndcgAtK(J, List.of("a", "b", "c"), 3), E);
    // Actual: c(1), a(3), x(0): 1/1 + 7/log2(3) + 0
    double actual = 1 + 7 / (Math.log(3) / Math.log(2));
    assertEquals(actual / ideal, RankingMetrics.ndcgAtK(J, List.of("c", "a", "x"), 3), E);
    assertEquals(7 / 7.0, RankingMetrics.ndcgAtK(J, List.of("a"), 1), E);
    assertEquals(0.0, RankingMetrics.ndcgAtK(J, List.of("x", "y"), 2), E);
  }

  @Test
  void undefinedCasesAreRefusedNotGuessed() {
    var none = Map.of("x", 0);
    assertThrows(
        IllegalArgumentException.class, () -> RankingMetrics.recallAtK(none, List.of("x"), 3));
    assertThrows(
        IllegalArgumentException.class, () -> RankingMetrics.reciprocalRank(none, List.of()));
    assertThrows(IllegalArgumentException.class, () -> RankingMetrics.ndcgAtK(none, List.of(), 3));
    assertEquals(2, RankingMetrics.falsePositives(List.of("x", "y"), 5));
    assertThrows(
        IllegalArgumentException.class, () -> RankingMetrics.precisionAtK(J, List.of(), 0));
    assertThrows(IllegalArgumentException.class, () -> RankingMetrics.judgments(Map.of("a", 4)));
  }
}

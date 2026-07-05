package io.qwenbridge.evaluation.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import io.qwenbridge.evaluation.model.EvaluationQuery;
import io.qwenbridge.evaluation.model.RelevanceLabel;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetrievalEvaluationMetricsTest {

  private final RetrievalEvaluationMetrics metrics = new RetrievalEvaluationMetrics();

  private final EvaluationQuery query =
      new EvaluationQuery(
          "q1",
          "gaming mouse",
          List.of(
              new RelevanceLabel("doc-1", 3),
              new RelevanceLabel("doc-2", 2),
              new RelevanceLabel("doc-3", 0),
              new RelevanceLabel("doc-4", 1)));

  @Test
  void shouldCalculatePrecisionAtK() {
    double value = metrics.precisionAtK(query, List.of("doc-3", "doc-1", "doc-2"), 3);

    assertThat(value).isEqualTo(2.0 / 3.0);
  }

  @Test
  void shouldCalculateRecallAtK() {
    double value = metrics.recallAtK(query, List.of("doc-3", "doc-1", "doc-2"), 3);

    assertThat(value).isEqualTo(2.0 / 3.0);
  }

  @Test
  void shouldCalculateReciprocalRank() {
    double value = metrics.reciprocalRank(query, List.of("doc-3", "doc-3b", "doc-2"));

    assertThat(value).isEqualTo(1.0 / 3.0);
  }

  @Test
  void shouldCalculateNdcgAtK() {
    double value = metrics.ndcgAtK(query, List.of("doc-2", "doc-3", "doc-1"), 3);

    assertThat(value).isCloseTo(0.692020, offset(0.000001));
  }

  @Test
  void shouldReturnZeroForEmptyOrInvalidInputs() {
    assertThat(metrics.precisionAtK(query, List.of(), 3)).isZero();
    assertThat(metrics.recallAtK(query, List.of(), 3)).isZero();
    assertThat(metrics.reciprocalRank(query, List.of())).isZero();
    assertThat(metrics.ndcgAtK(query, List.of(), 3)).isZero();
    assertThat(metrics.ndcgAtK(query, List.of("doc-1"), 0)).isZero();
  }
}

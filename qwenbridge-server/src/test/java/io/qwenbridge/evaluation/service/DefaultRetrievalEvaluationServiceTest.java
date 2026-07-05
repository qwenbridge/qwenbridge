package io.qwenbridge.evaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import io.qwenbridge.evaluation.model.EvaluationQuery;
import io.qwenbridge.evaluation.model.EvaluationResult;
import io.qwenbridge.evaluation.model.RelevanceLabel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultRetrievalEvaluationServiceTest {

  private final DefaultRetrievalEvaluationService service = new DefaultRetrievalEvaluationService();

  @Test
  void shouldAverageMetricsAcrossQueries() {
    List<EvaluationQuery> queries =
        List.of(
            new EvaluationQuery(
                "q1",
                "gaming mouse",
                List.of(
                    new RelevanceLabel("doc-mouse-1", 3),
                    new RelevanceLabel("doc-mouse-2", 2),
                    new RelevanceLabel("doc-keyboard-1", 0))),
            new EvaluationQuery(
                "q2",
                "standing desk",
                List.of(
                    new RelevanceLabel("doc-desk-1", 3),
                    new RelevanceLabel("doc-desk-2", 1),
                    new RelevanceLabel("doc-chair-1", 0))));

    Map<String, List<String>> rankedResults =
        Map.of(
            "q1", List.of("doc-keyboard-1", "doc-mouse-1", "doc-mouse-2"),
            "q2", List.of("doc-desk-1", "doc-chair-1", "doc-desk-2"));

    EvaluationResult result = service.evaluate(queries, rankedResults, 3);

    assertThat(result.queryCount()).isEqualTo(2);
    assertThat(result.precisionAtK()).isCloseTo(2.0 / 3.0, offset(0.000001));
    assertThat(result.recallAtK()).isCloseTo(1.0, offset(0.000001));
    assertThat(result.meanReciprocalRank()).isCloseTo(0.75, offset(0.000001));
    assertThat(result.ndcgAtK()).isCloseTo(0.824079, offset(0.000001));
  }

  @Test
  void shouldReturnZeroMetricsForEmptyInput() {
    EvaluationResult result = service.evaluate(List.of(), Map.of(), 3);

    assertThat(result.queryCount()).isZero();
    assertThat(result.precisionAtK()).isZero();
    assertThat(result.recallAtK()).isZero();
    assertThat(result.meanReciprocalRank()).isZero();
    assertThat(result.ndcgAtK()).isZero();
  }

  @Test
  void shouldTreatMissingRankedResultsAsEmptyResults() {
    EvaluationQuery query =
        new EvaluationQuery("q1", "gaming mouse", List.of(new RelevanceLabel("doc-mouse-1", 3)));

    EvaluationResult result = service.evaluate(List.of(query), Map.of(), 3);

    assertThat(result.queryCount()).isEqualTo(1);
    assertThat(result.precisionAtK()).isZero();
    assertThat(result.recallAtK()).isZero();
    assertThat(result.meanReciprocalRank()).isZero();
    assertThat(result.ndcgAtK()).isZero();
  }
}

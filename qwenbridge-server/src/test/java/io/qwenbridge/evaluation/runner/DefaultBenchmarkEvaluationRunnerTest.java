package io.qwenbridge.evaluation.runner;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.evaluation.dataset.BenchmarkDatasetLoader;
import io.qwenbridge.evaluation.model.BenchmarkEvaluationReport;
import io.qwenbridge.evaluation.policy.DefaultEvaluationThresholdPolicy;
import io.qwenbridge.evaluation.service.DefaultRetrievalEvaluationService;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultBenchmarkEvaluationRunnerTest {

  private final DefaultBenchmarkEvaluationRunner runner =
      new DefaultBenchmarkEvaluationRunner(
          new BenchmarkDatasetLoader(),
          new DefaultRetrievalEvaluationService(),
          new DefaultEvaluationThresholdPolicy());

  @Test
  void shouldRunEndToEndBenchmarkEvaluationAndPassGate() {
    String csv =
        """
        queryId,query,documentId,relevance
        q1,gaming mouse,doc-mouse-1,3
        q1,gaming mouse,doc-mouse-2,2
        q2,standing desk,doc-desk-1,3
        q2,standing desk,doc-desk-2,2
        """;

    Map<String, List<String>> rankedResults =
        Map.of(
            "q1", List.of("doc-mouse-1", "doc-mouse-2"),
            "q2", List.of("doc-desk-1", "doc-desk-2"));

    BenchmarkEvaluationReport report = runner.run(new StringReader(csv), rankedResults, 2);

    assertThat(report.passed()).isTrue();
    assertThat(report.result().queryCount()).isEqualTo(2);
    assertThat(report.gate().violations()).isEmpty();
  }

  @Test
  void shouldRunEndToEndBenchmarkEvaluationAndFailGate() {
    String csv =
        """
        queryId,query,documentId,relevance
        q1,gaming mouse,doc-mouse-1,3
        q1,gaming mouse,doc-mouse-2,2
        """;

    Map<String, List<String>> rankedResults = Map.of("q1", List.of("irrelevant-doc"));

    BenchmarkEvaluationReport report = runner.run(new StringReader(csv), rankedResults, 2);

    assertThat(report.passed()).isFalse();
    assertThat(report.result().queryCount()).isEqualTo(1);
    assertThat(report.gate().violations()).isNotEmpty();
  }
}

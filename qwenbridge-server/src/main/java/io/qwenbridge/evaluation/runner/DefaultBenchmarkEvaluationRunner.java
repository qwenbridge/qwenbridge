package io.qwenbridge.evaluation.runner;

import io.qwenbridge.evaluation.dataset.BenchmarkDatasetLoader;
import io.qwenbridge.evaluation.model.BenchmarkEvaluationReport;
import io.qwenbridge.evaluation.model.EvaluationGateResult;
import io.qwenbridge.evaluation.model.EvaluationQuery;
import io.qwenbridge.evaluation.model.EvaluationResult;
import io.qwenbridge.evaluation.policy.EvaluationThresholdPolicy;
import io.qwenbridge.evaluation.service.RetrievalEvaluationService;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class DefaultBenchmarkEvaluationRunner implements BenchmarkEvaluationRunner {

  private final BenchmarkDatasetLoader datasetLoader;
  private final RetrievalEvaluationService evaluationService;
  private final EvaluationThresholdPolicy thresholdPolicy;

  public DefaultBenchmarkEvaluationRunner(
      BenchmarkDatasetLoader datasetLoader,
      RetrievalEvaluationService evaluationService,
      EvaluationThresholdPolicy thresholdPolicy) {
    this.datasetLoader = Objects.requireNonNull(datasetLoader, "datasetLoader must not be null");
    this.evaluationService =
        Objects.requireNonNull(evaluationService, "evaluationService must not be null");
    this.thresholdPolicy =
        Objects.requireNonNull(thresholdPolicy, "thresholdPolicy must not be null");
  }

  @Override
  public BenchmarkEvaluationReport run(
      Reader benchmarkReader, Map<String, List<String>> rankedResultsByQueryId, int k) {
    Objects.requireNonNull(benchmarkReader, "benchmarkReader must not be null");
    Objects.requireNonNull(rankedResultsByQueryId, "rankedResultsByQueryId must not be null");

    List<EvaluationQuery> queries = datasetLoader.load(benchmarkReader);
    EvaluationResult result = evaluationService.evaluate(queries, rankedResultsByQueryId, k);
    EvaluationGateResult gate = thresholdPolicy.evaluate(result);

    return new BenchmarkEvaluationReport(result, gate);
  }
}

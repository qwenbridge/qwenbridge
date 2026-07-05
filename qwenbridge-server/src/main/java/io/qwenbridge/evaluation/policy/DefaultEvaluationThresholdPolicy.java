package io.qwenbridge.evaluation.policy;

import io.qwenbridge.evaluation.model.EvaluationGateResult;
import io.qwenbridge.evaluation.model.EvaluationResult;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DefaultEvaluationThresholdPolicy implements EvaluationThresholdPolicy {

  private static final double MIN_PRECISION_AT_K = 0.60;
  private static final double MIN_RECALL_AT_K = 0.60;
  private static final double MIN_MRR = 0.60;
  private static final double MIN_NDCG_AT_K = 0.70;

  @Override
  public EvaluationGateResult evaluate(EvaluationResult result) {
    if (result == null || result.queryCount() <= 0) {
      return EvaluationGateResult.failed(
          List.of("evaluation result must contain at least one query"));
    }

    List<String> violations = new ArrayList<>();

    requireAtLeast("precisionAtK", result.precisionAtK(), MIN_PRECISION_AT_K, violations);

    requireAtLeast("recallAtK", result.recallAtK(), MIN_RECALL_AT_K, violations);

    requireAtLeast("meanReciprocalRank", result.meanReciprocalRank(), MIN_MRR, violations);

    requireAtLeast("ndcgAtK", result.ndcgAtK(), MIN_NDCG_AT_K, violations);

    if (violations.isEmpty()) {
      return EvaluationGateResult.success();
    }

    return EvaluationGateResult.failed(violations);
  }

  private void requireAtLeast(
      String metric, double actual, double minimum, List<String> violations) {
    if (Double.isNaN(actual) || Double.isInfinite(actual) || actual < minimum) {
      violations.add("%s %.4f is below required minimum %.4f".formatted(metric, actual, minimum));
    }
  }
}

package io.qwenbridge.evaluation.policy;

import io.qwenbridge.evaluation.model.EvaluationGateResult;
import io.qwenbridge.evaluation.model.EvaluationResult;

public interface EvaluationThresholdPolicy {

  EvaluationGateResult evaluate(EvaluationResult result);
}

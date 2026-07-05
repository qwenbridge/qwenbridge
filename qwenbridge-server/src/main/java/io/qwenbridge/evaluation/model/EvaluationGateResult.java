package io.qwenbridge.evaluation.model;

import java.util.List;

public record EvaluationGateResult(boolean passed, List<String> violations) {

  public EvaluationGateResult {
    violations = List.copyOf(violations == null ? List.of() : violations);
  }

  public static EvaluationGateResult success() {
    return new EvaluationGateResult(true, List.of());
  }

  public static EvaluationGateResult failed(List<String> violations) {
    return new EvaluationGateResult(false, violations);
  }
}

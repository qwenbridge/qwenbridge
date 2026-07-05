package io.qwenbridge.evaluation.model;

public record BenchmarkEvaluationReport(EvaluationResult result, EvaluationGateResult gate) {

  public BenchmarkEvaluationReport {
    if (result == null) {
      throw new IllegalArgumentException("result must not be null");
    }

    if (gate == null) {
      throw new IllegalArgumentException("gate must not be null");
    }
  }

  public boolean passed() {
    return gate.passed();
  }
}

package io.qwenbridge.execution;

import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.Builder;

@Builder
public record ExecutionPlan(
    SearchMode mode, SearchBackend backend, List<ExecutionStep> steps, String reason) {
  public ExecutionPlan {
    Objects.requireNonNull(mode, "mode must not be null");
    Objects.requireNonNull(backend, "backend must not be null");
    Objects.requireNonNull(steps, "steps must not be null");

    steps = steps.stream().sorted(Comparator.comparingInt(ExecutionStep::order)).toList();

    if (steps.isEmpty()) {
      throw new IllegalArgumentException("steps must not be empty");
    }

    if (reason == null || reason.isBlank()) {
      reason = "No execution plan reason provided.";
    }
  }

  public boolean contains(ExecutionOperation operation) {
    return steps.stream().anyMatch(step -> step.operation() == operation);
  }
}

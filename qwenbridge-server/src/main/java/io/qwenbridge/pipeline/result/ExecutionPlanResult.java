package io.qwenbridge.pipeline.result;

import io.qwenbridge.execution.ExecutionPlan;
import lombok.Builder;

@Builder
public record ExecutionPlanResult(ExecutionPlan plan) {
  public static ExecutionPlanResult none() {
    return ExecutionPlanResult.builder().plan(null).build();
  }

  public boolean available() {
    return plan != null;
  }
}

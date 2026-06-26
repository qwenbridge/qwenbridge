package io.qwenbridge.pipeline.result;

import io.qwenbridge.execution.ExecutionPlan;

public record ExecutionPlanResult(
        ExecutionPlan plan
) {
    public static ExecutionPlanResult none() {
        return new ExecutionPlanResult(null);
    }

    public boolean available() {
        return plan != null;
    }
}

package io.qwenbridge.pipeline.result;

import io.qwenbridge.execution.ExecutionResult;

public record ExecutionResultResult(
        ExecutionResult result
) {
    public static ExecutionResultResult none() {
        return new ExecutionResultResult(null);
    }

    public boolean available() {
        return result != null;
    }
}

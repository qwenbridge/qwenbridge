package io.qwenbridge.pipeline.result;

import lombok.Builder;

import io.qwenbridge.execution.ExecutionResult;

@Builder
public record ExecutionResultResult(
        ExecutionResult result
) {
    public static ExecutionResultResult none() {
        return ExecutionResultResult.builder()
                .result(null)
                .build();
    }

    public boolean available() {
        return result != null;
    }
}

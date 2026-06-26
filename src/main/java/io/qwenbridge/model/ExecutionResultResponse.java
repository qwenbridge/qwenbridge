package io.qwenbridge.model;

import io.qwenbridge.execution.ExecutionOperation;

import java.util.List;

public record ExecutionResultResponse(
        boolean available,
        boolean executed,
        List<ExecutionOperation> operations,
        List<String> results,
        String reason
) {
    public static ExecutionResultResponse unavailable() {
        return new ExecutionResultResponse(false, false, List.of(), List.of(), null);
    }
}

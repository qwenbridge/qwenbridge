package io.qwenbridge.model;

import lombok.Builder;

import io.qwenbridge.execution.ExecutionOperation;

import java.util.List;

@Builder
public record ExecutionResultResponse(
        boolean available,
        boolean executed,
        List<ExecutionOperation> operations,
        List<String> results,
        String reason
) {
    public static ExecutionResultResponse unavailable() {
        return ExecutionResultResponse.builder()
                .available(false)
                .executed(false)
                .operations(List.of())
                .results(List.of())
                .reason(null)
                .build();
    }
}

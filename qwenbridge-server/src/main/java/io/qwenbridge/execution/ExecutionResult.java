package io.qwenbridge.execution;

import lombok.Builder;

import java.util.List;
import java.util.Objects;

@Builder
public record ExecutionResult(
        boolean executed,
        List<ExecutionOperation> operations,
        List<String> results,
        String reason
) {
    public ExecutionResult {
        Objects.requireNonNull(operations, "operations must not be null");
        Objects.requireNonNull(results, "results must not be null");

        if (reason == null || reason.isBlank()) {
            reason = "No execution result reason provided.";
        }
    }

    public static ExecutionResult skipped(String reason) {
        return ExecutionResult.builder()
                .executed(false)
                .operations(List.of())
                .results(List.of())
                .reason(reason)
                .build();
    }

    public static ExecutionResult completed(
            List<ExecutionOperation> operations,
            List<String> results,
            String reason
    ) {
        return ExecutionResult.builder()
                .executed(true)
                .operations(operations)
                .results(results)
                .reason(reason)
                .build();
    }
}

package io.qwenbridge.execution;

import java.util.List;
import java.util.Objects;

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
        return new ExecutionResult(false, List.of(), List.of(), reason);
    }

    public static ExecutionResult completed(
            List<ExecutionOperation> operations,
            List<String> results,
            String reason
    ) {
        return new ExecutionResult(true, operations, results, reason);
    }
}

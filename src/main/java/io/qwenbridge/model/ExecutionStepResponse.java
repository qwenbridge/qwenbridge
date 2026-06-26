package io.qwenbridge.model;

import io.qwenbridge.execution.ExecutionOperation;

public record ExecutionStepResponse(
        int order,
        ExecutionOperation operation,
        String reason
) {}

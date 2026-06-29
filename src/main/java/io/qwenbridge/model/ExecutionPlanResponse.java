package io.qwenbridge.model;

import lombok.Builder;

import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchMode;

import java.util.List;

@Builder
public record ExecutionPlanResponse(
        boolean available,
        SearchMode mode,
        SearchBackend backend,
        List<ExecutionStepResponse> steps,
        String reason
) {
    public static ExecutionPlanResponse unavailable() {
        return ExecutionPlanResponse.builder()
                .available(false)
                .mode(null)
                .backend(null)
                .steps(List.of())
                .reason(null)
                .build();
    }
}

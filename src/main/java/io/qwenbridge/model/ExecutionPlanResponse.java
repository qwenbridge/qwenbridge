package io.qwenbridge.model;

import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchMode;

import java.util.List;

public record ExecutionPlanResponse(
        boolean available,
        SearchMode mode,
        SearchBackend backend,
        List<ExecutionStepResponse> steps,
        String reason
) {
    public static ExecutionPlanResponse unavailable() {
        return new ExecutionPlanResponse(false, null, null, List.of(), null);
    }
}

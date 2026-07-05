package io.qwenbridge.model;

import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchMode;
import java.util.List;
import lombok.Builder;

@Builder
public record ExecutionPlanResponse(
    boolean available,
    SearchMode mode,
    SearchBackend backend,
    List<ExecutionStepResponse> steps,
    String reason) {
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

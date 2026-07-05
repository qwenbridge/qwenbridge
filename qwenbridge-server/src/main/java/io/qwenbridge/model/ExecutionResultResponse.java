package io.qwenbridge.model;

import io.qwenbridge.execution.ExecutionOperation;
import java.util.List;
import lombok.Builder;

@Builder
public record ExecutionResultResponse(
    boolean available,
    boolean executed,
    List<ExecutionOperation> operations,
    List<String> results,
    String reason) {
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

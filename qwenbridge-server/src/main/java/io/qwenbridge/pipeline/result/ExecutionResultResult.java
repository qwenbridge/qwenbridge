package io.qwenbridge.pipeline.result;

import io.qwenbridge.execution.ExecutionResult;
import lombok.Builder;

@Builder
public record ExecutionResultResult(ExecutionResult result) {
  public static ExecutionResultResult none() {
    return ExecutionResultResult.builder().result(null).build();
  }

  public boolean available() {
    return result != null;
  }
}

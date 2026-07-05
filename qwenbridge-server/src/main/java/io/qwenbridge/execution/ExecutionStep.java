package io.qwenbridge.execution;

import java.util.Objects;

public record ExecutionStep(int order, ExecutionOperation operation, String reason) {
  public ExecutionStep {
    if (order < 0) {
      throw new IllegalArgumentException("order must not be negative");
    }

    Objects.requireNonNull(operation, "operation must not be null");

    if (reason == null || reason.isBlank()) {
      reason = "No execution step reason provided.";
    }
  }
}

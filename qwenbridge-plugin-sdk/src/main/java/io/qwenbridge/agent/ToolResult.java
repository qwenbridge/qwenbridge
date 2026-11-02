package io.qwenbridge.agent;

import java.util.List;
import java.util.Map;

/**
 * A tool call's outcome. {@code DENIED} carries the policy reason codes; the handler did not run.
 */
public record ToolResult(Status status, Map<String, Object> output, List<String> reasons) {
  public enum Status {
    COMPLETED,
    DENIED,
    FAILED,
    TIMEOUT,
    CANCELLED
  }

  public ToolResult {
    output = output == null ? Map.of() : Map.copyOf(output);
    reasons = reasons == null ? List.of() : List.copyOf(reasons);
  }

  public boolean completed() {
    return status == Status.COMPLETED;
  }
}

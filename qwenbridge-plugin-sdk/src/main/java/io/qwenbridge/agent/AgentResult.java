package io.qwenbridge.agent;

import java.util.List;
import java.util.Map;

/** A finished run (QB-226): status, structured output, the recorded steps and why it ended. */
public record AgentResult(
    String runId,
    String agentId,
    Status status,
    Map<String, Object> output,
    List<Step> steps,
    String error) {
  public enum Status {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    TIMED_OUT
  }

  /** One recorded step: a tool call, a delegation or a note. */
  public record Step(
      int index, String kind, String name, String status, List<String> reasons, long durationMs) {
    public Step {
      reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
  }

  public AgentResult {
    output = output == null ? Map.of() : Map.copyOf(output);
    steps = steps == null ? List.of() : List.copyOf(steps);
  }
}

package io.qwenbridge.agent;

import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * One authorized tool call. {@code tenant} and {@code subject} come from the server (the caller of
 * the agent run), never from the input, so input cannot move a call into another tenant.
 */
public record ToolCall(
    String toolId,
    String runId,
    String tenant,
    String subject,
    Map<String, Object> input,
    BooleanSupplier cancelled) {
  public ToolCall {
    input = input == null ? Map.of() : Map.copyOf(input);
  }
}

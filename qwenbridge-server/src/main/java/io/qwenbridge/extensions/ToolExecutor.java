package io.qwenbridge.extensions;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.agent.ToolCall;
import io.qwenbridge.agent.ToolResult;
import io.qwenbridge.core.policy.PolicyDecision;
import io.qwenbridge.core.policy.Resource;
import io.qwenbridge.security.ApiIdentity;
import io.qwenbridge.security.Authorization;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/**
 * The tool-execution boundary (QB-227, QB-231). Every call is authorized here, as the run's caller,
 * by the central policy engine for the tool's declared operation in the caller's own tenant. A
 * denial returns DENIED with the reason codes and the handler never runs. Agent declarations, tool
 * input and model output play no part in the decision.
 *
 * <p>Handlers run on their own virtual thread with the tool's timeout (bounded by the run's
 * deadline) and stop on cancellation when they cooperate; failures become typed results.
 */
@jakarta.inject.Singleton
public final class ToolExecutor {
  static final int MAX_PAYLOAD_BYTES = 256 * 1024;
  private static final ObjectMapper JSON = new ObjectMapper();

  private final ExtensionRegistry registry;
  private final ToolAudit audit;

  @jakarta.inject.Inject
  public ToolExecutor(ExtensionRegistry registry, ToolAudit audit) {
    this.registry = registry;
    this.audit = audit;
  }

  public ToolResult invoke(
      ApiIdentity.Principal caller,
      String runId,
      String toolId,
      Map<String, Object> input,
      BooleanSupplier cancelled,
      Instant deadline) {
    var found = registry.tool(toolId);
    if (found.isEmpty())
      return finish(caller, runId, toolId, "", ToolResult.Status.DENIED, List.of("UNKNOWN_TOOL"));
    var definition = found.get().definition();
    PolicyDecision decision =
        Authorization.decide(
            caller,
            definition.operation(),
            Resource.of(definition.resourceType(), caller.tenant()).via("tool:" + toolId));
    if (!decision.allowed())
      return finish(
          caller,
          runId,
          toolId,
          definition.operation(),
          ToolResult.Status.DENIED,
          decision.reasons().stream().map(Enum::name).toList());
    if (size(input) > MAX_PAYLOAD_BYTES)
      return finish(
          caller,
          runId,
          toolId,
          definition.operation(),
          ToolResult.Status.FAILED,
          List.of("INPUT_TOO_LARGE"));
    if (cancelled.getAsBoolean())
      return finish(
          caller,
          runId,
          toolId,
          definition.operation(),
          ToolResult.Status.CANCELLED,
          List.of("CANCELLED"));
    Duration left = Duration.between(Instant.now(), deadline);
    Duration timeout = left.compareTo(definition.timeout()) < 0 ? left : definition.timeout();
    if (timeout.isNegative() || timeout.isZero())
      return finish(
          caller,
          runId,
          toolId,
          definition.operation(),
          ToolResult.Status.TIMEOUT,
          List.of("DEADLINE_EXCEEDED"));
    var call = new ToolCall(toolId, runId, caller.tenant(), caller.subject(), input, cancelled);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var future = executor.submit(() -> found.get().handler().handle(call));
      long end = System.nanoTime() + timeout.toNanos();
      while (true) {
        if (cancelled.getAsBoolean()) {
          future.cancel(true);
          return finish(
              caller,
              runId,
              toolId,
              definition.operation(),
              ToolResult.Status.CANCELLED,
              List.of("CANCELLED"));
        }
        long remaining = end - System.nanoTime();
        if (remaining <= 0) {
          future.cancel(true);
          return finish(
              caller,
              runId,
              toolId,
              definition.operation(),
              ToolResult.Status.TIMEOUT,
              List.of("TIMEOUT"));
        }
        try {
          Map<String, Object> output =
              future.get(
                  Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(50)), TimeUnit.NANOSECONDS);
          if (size(output) > MAX_PAYLOAD_BYTES)
            return finish(
                caller,
                runId,
                toolId,
                definition.operation(),
                ToolResult.Status.FAILED,
                List.of("OUTPUT_TOO_LARGE"));
          audit.record(
              entry(
                  caller,
                  runId,
                  toolId,
                  definition.operation(),
                  ToolResult.Status.COMPLETED,
                  List.of("ALLOWED")));
          return new ToolResult(
              ToolResult.Status.COMPLETED, output == null ? Map.of() : output, List.of("ALLOWED"));
        } catch (TimeoutException slice) {
          // poll cancellation and the overall timeout again
        }
      }
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      return finish(
          caller,
          runId,
          toolId,
          definition.operation(),
          ToolResult.Status.FAILED,
          List.of("TOOL_ERROR:" + (cause == null ? "unknown" : cause.getClass().getSimpleName())));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return finish(
          caller,
          runId,
          toolId,
          definition.operation(),
          ToolResult.Status.CANCELLED,
          List.of("CANCELLED"));
    }
  }

  private ToolResult finish(
      ApiIdentity.Principal caller,
      String runId,
      String tool,
      String operation,
      ToolResult.Status status,
      List<String> reasons) {
    audit.record(entry(caller, runId, tool, operation, status, reasons));
    return new ToolResult(status, Map.of(), reasons);
  }

  private static ToolAudit.Entry entry(
      ApiIdentity.Principal caller,
      String runId,
      String tool,
      String operation,
      ToolResult.Status status,
      List<String> reasons) {
    return new ToolAudit.Entry(
        Instant.now(),
        runId,
        caller.tenant(),
        caller.subject(),
        tool,
        operation,
        status.name(),
        reasons);
  }

  private static int size(Object value) {
    try {
      return value == null ? 0 : JSON.writeValueAsBytes(value).length;
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      return Integer.MAX_VALUE;
    }
  }
}

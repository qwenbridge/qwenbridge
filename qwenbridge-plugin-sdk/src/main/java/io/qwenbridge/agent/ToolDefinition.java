package io.qwenbridge.agent;

import io.qwenbridge.plugin.PluginManifest;
import java.time.Duration;
import java.util.Objects;

/**
 * A tool an agent may call (QB-227).
 *
 * <ul>
 *   <li>{@code operation} is the policy operation the call is authorized as (for example {@code
 *       search} or {@code catalog:write}); the server checks it against the caller's own
 *       permissions at every call, so declaring a tool grants nothing.
 *   <li>{@code effect} must match the operation's classification (READ or MUTATE); a mutating
 *       operation cannot be registered as a read.
 *   <li>{@code resourceType} is the kind of resource the call touches, always in the caller's
 *       tenant.
 * </ul>
 */
public record ToolDefinition(
    String id,
    String operation,
    Effect effect,
    String resourceType,
    String description,
    Duration timeout) {

  public enum Effect {
    READ,
    MUTATE
  }

  public ToolDefinition {
    PluginManifest.validExtensionId(id);
    if (operation == null || !operation.matches("[a-z][a-z0-9:.-]{0,63}"))
      throw new IllegalArgumentException("Invalid tool operation");
    Objects.requireNonNull(effect, "effect is required");
    if (resourceType == null || !resourceType.matches("[a-z][a-z0-9-]{0,31}"))
      throw new IllegalArgumentException("Invalid tool resource type");
    description = description == null ? "" : description;
    timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
    if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(5)) > 0)
      throw new IllegalArgumentException("Tool timeout must be 1 ms .. 5 min");
  }
}

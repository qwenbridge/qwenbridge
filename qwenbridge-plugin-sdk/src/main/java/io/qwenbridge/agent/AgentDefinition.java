package io.qwenbridge.agent;

import io.qwenbridge.plugin.PluginManifest;
import java.time.Duration;
import java.util.Set;

/**
 * An agent (QB-226): the tools it may use (an allow-list that can only narrow the caller's
 * permissions), the agents it may delegate to, and its bounds.
 */
public record AgentDefinition(
    String id,
    String description,
    Set<String> tools,
    Set<String> delegates,
    int maxSteps,
    Duration timeout) {
  public AgentDefinition {
    PluginManifest.validExtensionId(id);
    description = description == null ? "" : description;
    tools = tools == null ? Set.of() : Set.copyOf(tools);
    delegates = delegates == null ? Set.of() : Set.copyOf(delegates);
    for (String t : tools) PluginManifest.validExtensionId(t);
    for (String d : delegates) PluginManifest.validExtensionId(d);
    if (maxSteps < 1 || maxSteps > 1_000)
      throw new IllegalArgumentException("maxSteps must be 1..1000");
    timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
    if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(15)) > 0)
      throw new IllegalArgumentException("Agent timeout must be 1 ms .. 15 min");
  }
}

package io.qwenbridge.extensions;

import io.qwenbridge.agent.Agent;
import io.qwenbridge.agent.AgentDefinition;
import io.qwenbridge.agent.ToolDefinition;
import io.qwenbridge.agent.ToolHandler;
import io.qwenbridge.core.policy.OperationSpec;
import io.qwenbridge.security.Authorization;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The registered tools and agents, each owned by one plugin (QB-225, QB-227). Registration is the
 * only extension point: a tool must name an operation the central policy knows, with the same
 * effect classification, so a mutating operation can never be registered as a read.
 */
@jakarta.inject.Singleton
public final class ExtensionRegistry {

  public record RegisteredTool(String plugin, ToolDefinition definition, ToolHandler handler) {}

  public record RegisteredAgent(String plugin, AgentDefinition definition, Agent agent) {}

  private final Map<String, RegisteredTool> tools = new ConcurrentHashMap<>();
  private final Map<String, RegisteredAgent> agents = new ConcurrentHashMap<>();

  void addTool(String plugin, ToolDefinition definition, ToolHandler handler) {
    OperationSpec spec =
        Authorization.CATALOG
            .find(definition.operation())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Tool "
                            + definition.id()
                            + " names unknown operation "
                            + definition.operation()));
    boolean mutating = definition.effect() == ToolDefinition.Effect.MUTATE;
    if (mutating != spec.mutating())
      throw new IllegalArgumentException(
          "Tool "
              + definition.id()
              + " misclassifies operation "
              + definition.operation()
              + " (it is "
              + spec.effect()
              + ")");
    if (tools.putIfAbsent(definition.id(), new RegisteredTool(plugin, definition, handler)) != null)
      throw new IllegalArgumentException("Tool " + definition.id() + " is already registered");
  }

  void addAgent(String plugin, AgentDefinition definition, Agent agent) {
    if (agents.putIfAbsent(definition.id(), new RegisteredAgent(plugin, definition, agent)) != null)
      throw new IllegalArgumentException("Agent " + definition.id() + " is already registered");
  }

  /** Removes everything a plugin registered (stop, failure or rollback). */
  void removePlugin(String plugin) {
    tools.values().removeIf(t -> t.plugin().equals(plugin));
    agents.values().removeIf(a -> a.plugin().equals(plugin));
  }

  public Optional<RegisteredTool> tool(String id) {
    return Optional.ofNullable(tools.get(id));
  }

  public Optional<RegisteredAgent> agent(String id) {
    return Optional.ofNullable(agents.get(id));
  }

  public List<ToolDefinition> tools() {
    return tools.values().stream()
        .map(RegisteredTool::definition)
        .sorted(java.util.Comparator.comparing(ToolDefinition::id))
        .toList();
  }

  public List<AgentDefinition> agents() {
    return agents.values().stream()
        .map(RegisteredAgent::definition)
        .sorted(java.util.Comparator.comparing(AgentDefinition::id))
        .toList();
  }

  long count(String plugin) {
    return tools.values().stream().filter(t -> t.plugin().equals(plugin)).count()
        + agents.values().stream().filter(a -> a.plugin().equals(plugin)).count();
  }
}

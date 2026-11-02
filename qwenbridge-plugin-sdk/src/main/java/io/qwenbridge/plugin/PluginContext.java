package io.qwenbridge.plugin;

import io.qwenbridge.agent.Agent;
import io.qwenbridge.agent.AgentDefinition;
import io.qwenbridge.agent.ToolDefinition;
import io.qwenbridge.agent.ToolHandler;
import java.util.Map;

/** What a plugin receives on start (QB-225): its identity, configuration and the registrar. */
public interface PluginContext {

  String pluginId();

  /** Operator configuration for this plugin (never secrets in clear text). */
  Map<String, String> configuration();

  /** The approved extension points. Anything not declared in the manifest is refused. */
  Registrar registrar();

  /** The only ways a plugin can contribute executable capabilities. */
  interface Registrar {
    void tool(ToolDefinition definition, ToolHandler handler);

    void agent(AgentDefinition definition, Agent agent);
  }
}

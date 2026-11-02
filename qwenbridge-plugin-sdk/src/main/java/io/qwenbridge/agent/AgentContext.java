package io.qwenbridge.agent;

import java.util.Map;

/** What a running agent can do (QB-226). Every tool call is authorized as the run's caller. */
public interface AgentContext {

  /** Run identity: who it runs for. Read-only; the agent cannot change it. */
  String runId();

  String tenant();

  String subject();

  /** Calls an approved tool; never throws for a denial (the result says DENIED). */
  ToolResult tool(String toolId, Map<String, Object> input);

  /**
   * Runs an approved delegate agent as part of this run. The delegate's tools are narrowed to those
   * both agents may use, so delegation never widens permissions.
   */
  AgentResult delegate(String agentId, Map<String, Object> input);

  /** Records a free-form step (for inspection); does not count as a tool call. */
  void note(String name, String detail);

  /** True once the run was cancelled or ran out of time; cooperative agents check it. */
  boolean cancelled();
}

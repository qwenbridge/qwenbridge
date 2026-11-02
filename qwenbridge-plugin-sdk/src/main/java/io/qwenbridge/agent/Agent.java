package io.qwenbridge.agent;

import java.util.Map;

/**
 * The Agent SPI (QB-226), separate from the Connector SPI: an agent orchestrates work only through
 * {@link AgentContext}: approved tools, delegation to approved agents, and step records. It has no
 * infrastructure access of its own and inherits no permissions from what it declares.
 */
@FunctionalInterface
public interface Agent {
  /** Runs once; returns the structured output. Throwing fails the run. */
  Map<String, Object> run(AgentContext context, Map<String, Object> input) throws Exception;
}

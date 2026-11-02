package io.qwenbridge.agent;

import java.util.Map;

/** Executes one authorized tool call. It receives no way to call other tools or agents. */
@FunctionalInterface
public interface ToolHandler {
  Map<String, Object> handle(ToolCall call) throws Exception;
}

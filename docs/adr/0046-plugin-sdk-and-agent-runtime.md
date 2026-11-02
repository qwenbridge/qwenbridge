# ADR 0046: Plugin SDK 1.0 and the Agent Runtime

## Status

Accepted (QwenBridge 1.0, Section 4 Phase 31: QB-225 – QB-231)

## Context

Extensions were limited to the legacy `QwenBridgePlugin` interface. It had no manifest, no
versioning, no lifecycle and no notion of agents or tools. Agents that act on model output need a
boundary where every action is authorized, bounded and recorded.

## Decision

1. **Plugin SDK 1.0** (`qwenbridge-plugin-sdk`, no dependencies):
   - **Manifest:** `META-INF/qwenbridge/plugin.json`, with ID, publisher, version, SDK and runtime
     version ranges, entry point, dependencies, and the tools and agents the plugin declares.
   - **Lifecycle:** `Plugin.start(PluginContext)` and `stop()`.
   - **Agent SPI:** `ToolDefinition`, `ToolHandler`, `AgentDefinition`, `Agent`, `AgentContext`,
     `ToolResult` and `AgentResult`.
   - **Legacy interface:** `QwenBridgePlugin` is unchanged.
2. **Plugin host.**
   - **Validation first:** manifests are validated before any plugin class is loaded.
   - **Activation:** dependencies start first, with compatibility checks and a start timeout.
   - **Failure:** a failed start rolls back all of the plugin's registrations.
3. **Tool boundary.** Agents act only through tools, and each call is authorized by the central
   policy (ADR 0023) as the run's caller. An agent holds no permissions of its own.
   - **Agent scope:** allow-lists per agent; delegation intersects them.
   - **Limits:** step and payload caps, deadlines and cooperative cancellation.
   - **Audit:** decisions are recorded without inputs.
4. **API.** `/api/v2/extensions` and `/api/v2/agents` expose the inventory, the catalog and the
   runs. Only the owner can see or cancel a run.
5. **No sandbox in 1.0.** Plugins are trusted code in the server JVM. The limits that are and are
   not enforced are documented in `docs/security/plugin-isolation.md`.

## Consequences

- **Packs can build on it.** The pack batches (QB-232 onwards) can ship agents and tools as plugins
  through one validated path.
- **Security tests.** `AgentAuthorizationTest` covers unauthorized and mutating tools, forged
  approvals, tenant changes, prompt injection, delegation, limits and audit secrecy.
- **Untrusted publishers still need isolation.** CPU, memory, file, network and JVM-level actions are
  not contained. Plugins from untrusted publishers need process or container isolation, which is
  future work.

# Plugin and Agent Isolation

QwenBridge 1.0 runs plugins **inside the server JVM**. It has no sandbox for untrusted code. Install
only plugins whose publisher you trust as much as the server itself. This page lists what the
plugin host enforces and what it does not.

## What is enforced

| Area | Enforcement |
|---|---|
| **Before any plugin code runs** | The manifest (`META-INF/qwenbridge/plugin.json`, at most 64 KB) is read from the jar without loading classes. Invalid manifests, duplicate plugin IDs and incompatible SDK or runtime ranges are rejected |
| **Declarations** | A plugin can register only the tools and agents its manifest declares. A tool must name an operation from the central policy catalog, and its declared effect (READ or MUTATE) must match that operation. Any violation fails the plugin and rolls back everything it registered |
| **Authorization** | Every tool call is authorized by the central policy as the **caller of the agent run**, with the caller's tenant. An agent has no permissions of its own. Tool input cannot change the tenant or carry an approval, and model text cannot grant anything |
| **Agent scope** | An agent can call only the tools in its own allow-list. Delegation intersects allow-lists (it never widens them) and is limited to 3 levels |
| **Limits** | Wall-clock timeouts (plugin start: `qwenbridge.plugins.start-timeout`, default 10 s; tool: at most 5 min; agent run: at most 15 min); at most 1,000 steps per run; tool input and output at most 256 KB each |
| **Cancellation** | Cancelling a run or reaching its deadline stops further tool calls and signals the running tool |
| **Visibility** | Runs are visible and cancellable only by the subject that started them |
| **Audit** | Every tool decision (allowed, denied, failed, timed out, cancelled) is recorded with its reasons. Inputs and outputs are not recorded |

## What is not enforced

These need process or container isolation, which 1.0 does not provide:
- **CPU and memory.** A plugin can use as much CPU and heap as the JVM allows.
- **Cooperative cancellation only.** A tool that ignores its cancellation signal keeps its thread
  until it returns, although its result is discarded.
- **Files, network and environment.** A plugin can read files, open connections and read
  environment variables available to the server process. This includes configured secrets.
- **The JVM itself.** A plugin can call `System.exit`, use reflection on server classes or start
  threads of its own.

## Recommendations

- Treat installing a plugin as deploying server code: review it, pin its version and record its
  checksum.
- Keep `qwenbridge.plugins.directory` writable only by the deployment process.
- Run plugins from untrusted publishers in a separate process or container, reached through a
  connector, until a future version provides out-of-process plugins.

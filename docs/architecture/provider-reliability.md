# AI Provider Reliability

## Launch Provider Scope

V6 launches with a single production AI provider:

- Provider ID: `ollama`
- Chat model: configured by `qwenbridge.ai.ollama.chat-model`
- Embedding model: configured by `qwenbridge.ai.ollama.embedding-model`

Hosted provider support is intentionally deferred. The provider abstraction is
already present, but the public launch contract is deterministic Ollama routing.

## Provider Configuration Contract

The Ollama provider is configured through:

```yaml
qwenbridge:
  ai:
    provider: ollama
    ollama:
      base-url: http://localhost:11434
      chat-model: qwen2.5
      embedding-model: bge-m3
      connect-timeout: 5s
      read-timeout: 60s
      retry-count: 1
      streaming-enabled: false
```

| Setting | Purpose |
| --- | --- |
| `qwenbridge.ai.provider` | Active provider ID. V6 supports `ollama`. |
| `qwenbridge.ai.ollama.base-url` | Ollama HTTP base URL. |
| `qwenbridge.ai.ollama.chat-model` | Model used for chat/reasoning calls. |
| `qwenbridge.ai.ollama.embedding-model` | Model used for embedding calls. |
| `qwenbridge.ai.ollama.connect-timeout` | TCP connect timeout. |
| `qwenbridge.ai.ollama.read-timeout` | Maximum provider response wait time. |
| `qwenbridge.ai.ollama.retry-count` | Bounded retry attempts for transient provider failures. |
| `qwenbridge.ai.ollama.streaming-enabled` | Provider streaming capability flag. V6 keeps public token streaming deferred. |

## Timeout Policy

Provider calls must be bounded. A provider call must not block the request
pipeline indefinitely.

- Connect timeout is enforced by the Reactor Netty HTTP client.
- Read timeout is enforced by the Reactor Netty HTTP client and the blocking
  provider boundary.
- Timeout failures are surfaced through `AIException` and mapped by the public
  REST API to `502 AI_PROVIDER_ERROR`.

## Retry Policy

V6 uses a small bounded retry strategy inside the provider client only.

- Retries happen at the Ollama HTTP boundary.
- Retries are configured by `retry-count`.
- `retry-count: 0` disables retries.
- When retries are exhausted, the provider returns a deterministic failure.
- Retry state is not exposed to public API clients.

## Failover Policy

V6 does not perform automatic AI provider failover.

Reasoning:

- Only Ollama is supported for launch.
- Silent failover can make latency, model behavior, and cost attribution
  unpredictable.
- Deterministic failure is easier for clients and operators to reason about.

When Ollama is unavailable, QwenBridge returns a controlled provider failure:

```text
HTTP 502 Bad Gateway
code: AI_PROVIDER_ERROR
```

## Cost Tracking Scope

V6 tracks provider usage only at the operational metadata level:

- Request count
- Latency
- Provider ID
- Model ID

Monetary cost calculation is deferred until hosted providers are introduced.

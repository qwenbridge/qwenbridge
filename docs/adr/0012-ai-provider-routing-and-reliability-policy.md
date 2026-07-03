# ADR 0012: AI Provider Routing and Reliability Policy

## Status

Accepted

## Context

QwenBridge has a provider abstraction, but V6 is a public hardening release with
an Ollama-only launch target. Provider failures must be deterministic and easy
to operate.

## Decision

V6 uses the configured provider ID to route AI requests. The only supported
launch provider is `ollama`.

The provider configuration contract includes:

- Provider ID
- Chat model ID
- Embedding model ID
- Base URL
- Connect timeout
- Read timeout
- Retry count
- Streaming enabled flag

Provider calls use bounded timeouts and bounded retries. Automatic provider
failover is intentionally not implemented in V6.

## Failure Semantics

If Ollama is unavailable, times out, returns an error, or produces an invalid
empty response, the provider raises `AIException`. The public API maps that
failure to:

```text
HTTP 502 Bad Gateway
AI_PROVIDER_ERROR
```

## Consequences

Positive:

- Provider behavior is deterministic.
- Client-facing failure semantics are stable.
- Operational debugging is simpler.
- Future hosted provider support can be introduced explicitly.

Trade-offs:

- V6 does not hide provider outages through failover.
- High availability depends on the configured Ollama runtime.
- Monetary provider cost tracking is deferred.

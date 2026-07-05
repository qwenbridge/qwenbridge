# Health

QwenBridge exposes health and readiness information for local development, CI verification, and production operations.

## Health endpoint

```text
GET /api/v1/health
```

The health endpoint reports API availability and operational state.

## Dependency health

QwenBridge can check the health of external dependencies such as:

- Ollama
- OpenSearch
- Redis

Each dependency check should report:

- dependency name
- status
- reason or error summary
- latency where available

## Readiness

A deployment should be considered ready only when required dependencies and production configuration are valid.

Readiness should fail when a required dependency is unavailable and the selected deployment profile requires that dependency.

## Health usage

Use health checks for:

- container readiness probes
- deployment verification
- release verification
- operational debugging
- dependency outage detection

Do not use health checks as a substitute for full release verification.

# QwenBridge Operational Health

## Endpoints

| Endpoint | Purpose | Contract |
| --- | --- | --- |
| `GET /api/v1/health/live` | Process liveness | Returns `UP` when the application process can serve HTTP. It does not check remote dependencies. |
| `GET /api/v1/health/ready` | Dependency-aware readiness | Checks Redis, OpenSearch and Ollama and returns aggregate status. |
| `GET /actuator/health/liveness` | Spring Boot probe | Kubernetes/container liveness probe. |
| `GET /actuator/health/readiness` | Spring Boot probe | Kubernetes/container readiness probe. |

## Status model

`UP` means all checked dependencies are available. `DEGRADED` means at least one optional or recoverable dependency is unavailable but the app can still respond through fallback behavior. `DOWN` is reserved for non-recoverable readiness failure.

Dependency responses expose sanitized reasons only: `available`, `not_configured`, `empty_ping_response`, or `unavailable`. Internal exception messages, hosts, credentials and stack traces are intentionally not returned.

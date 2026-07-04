# QwenBridge Metrics

Metrics are exported through Spring Boot Actuator and Micrometer.

| Metric | Tags | Meaning |
| --- | --- | --- |
| `qwenbridge.http.server.requests` | `method`, `path`, `status` | Request count and latency by normalized path and status code. |
| `qwenbridge.ai.provider.requests` | `provider`, `operation`, `outcome` | Ollama latency and success/failure counts. |
| `qwenbridge.opensearch.requests` | `operation`, `outcome` | OpenSearch latency and success/failure counts. |
| `qwenbridge.redis.cache.events` | `cache`, `result` | Redis cache hit, miss, put, evict and fallback events. |
| `qwenbridge.sse.sessions.active` | none | Current active SSE sessions. |
| `qwenbridge.sse.sessions.opened` | none | Total opened SSE sessions. |
| `qwenbridge.sse.sessions.closed` | `reason` | Closed sessions by reason. |
| `qwenbridge.sse.events` | `event` | SSE event count by event name. |
| `qwenbridge.ratelimit.decisions` | `policy`, `decision` | Rate-limit allowed/rejected decisions. |

Naming convention: all custom metrics use the `qwenbridge.` prefix, lower-case dot-separated domains, and bounded-cardinality tags.

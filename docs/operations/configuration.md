# QwenBridge Configuration

Profiles:

- `local`: developer defaults, local Ollama/OpenSearch/Redis.
- `docker`: Docker Compose service names.
- `test`: disables abuse protection and Redis cache by default.
- `production`: fail-fast required runtime configuration.

Required production environment variables:

| Variable | Purpose |
| --- | --- |
| `QWENBRIDGE_CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins. |
| `QWENBRIDGE_AI_OLLAMA_BASE_URL` | Ollama base URL. |
| `QWENBRIDGE_SEARCH_OPENSEARCH_BASE_URL` | OpenSearch base URL. |
| `QWENBRIDGE_ANALYSIS_CACHE_REDIS_HOST` | Redis host. |
| `QWENBRIDGE_ANALYSIS_CACHE_REDIS_PORT` | Redis port, default `6379`. |

Safe defaults exist for local/docker/test, but production must provide explicit dependency and CORS configuration.

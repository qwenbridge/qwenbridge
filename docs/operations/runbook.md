# Runbook

This runbook provides operational steps for common QwenBridge issues.

## API is not responding

Check the server process:

```bash
curl -fsS http://localhost:8080/api/v1/health
```

Check logs for startup errors, port conflicts, configuration validation failures, and dependency failures.

## OpenSearch unavailable

Check OpenSearch:

```bash
curl -fsS http://localhost:9200
```

Check:

- container status
- index existence
- network connectivity
- configured OpenSearch URL
- timeout settings

## Ollama unavailable

Check Ollama:

```bash
curl -fsS http://localhost:11434/api/tags
```

Check:

- Ollama process or container status
- configured base URL
- model availability
- timeout settings

## Redis unavailable

Check Redis container or service status.

If Redis is used for cache only, QwenBridge may degrade depending on configuration. If Redis is required for production rate limiting, treat the outage as production-impacting.

## SSE stream does not receive terminal events

Check:

- original request ID
- stream request ID
- streaming session registry
- pipeline event publisher
- terminal policy
- disconnect logs

Run SSE verification:

```bash
bash scripts/verification/06-sse.sh
```

## Release verification fails

Run the full release script:

```bash
bash scripts/verify-release.sh
```

Inspect the failing section under `scripts/verification/` and rerun only the relevant dependency checks after fixing the issue.

## Performance regression

Run the k6 scripts under:

```text
scripts/performance
```

Compare current results against release evidence and previous baseline logs.

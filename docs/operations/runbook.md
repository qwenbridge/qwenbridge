# QwenBridge Operations Runbook

## Startup smoke test

```bash
curl -fsS http://localhost:8080/api/v1/health/live
curl -fsS http://localhost:8080/api/v1/health/ready
curl -fsS http://localhost:8080/actuator/metrics/qwenbridge.http.server.requests
```

## Degraded dependency check

If `/api/v1/health/ready` returns `DEGRADED`, inspect `dependencies[].name`, `dependencies[].status` and sanitized `dependencies[].reason`. Then correlate logs by `requestId` or `traceId`.

## Rollback

1. Stop the current container or Compose deployment.
2. Start the previous immutable image tag.
3. Verify liveness, readiness and one search request.
4. Preserve logs and release evidence for incident review.

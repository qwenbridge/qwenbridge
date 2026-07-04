# V8 Abuse Protection Policy

QwenBridge applies runtime abuse limits before API requests enter the pipeline.

## Limits

- Per-IP fixed-window rate limit.
- Per-API-key fixed-window rate limit through `X-API-Key`.
- Request body size limit.
- Concurrent SSE stream limit.
- AI request quota for AI-heavy endpoints.
- Redis-backed distributed limiting when Redis is available.
- In-memory fallback when Redis is unavailable, unless fail-open is configured.

## Response contract

Rejected requests return HTTP `429 Too Many Requests` with `ApiError.code = RATE_LIMITED`.

Response headers:

- `Retry-After`
- `X-RateLimit-Limit`
- `X-RateLimit-Remaining`
- `X-RateLimit-Reset`
- `X-RateLimit-Policy`

## Configuration

All knobs are under `qwenbridge.abuse` in `application.yml`.

# REST API

Base path: `/api/v1`

## Search analysis

```text
POST /api/v1/search/analyze
```

Submit a search query for pipeline analysis and execution.

Use `X-Request-Id` to supply a client correlation identifier. If omitted, the server creates one.

## AI chat

```text
POST /api/v1/ai/chat
```

Uses the configured AI provider for chat behavior.

## Health

```text
GET /api/v1/health
```

Returns API and operational health information.

## Version

```text
GET /api/v1/version
```

Returns API version metadata.

## Errors

Errors use the public `ApiError` contract and include a stable error code, HTTP status, message, and request ID where available. Clients should use error codes rather than parsing messages.

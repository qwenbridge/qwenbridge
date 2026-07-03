# REST API

## Overview

QwenBridge exposes a versioned public API for AI-native search analysis and streaming pipeline events.

Current public API version: `v1`

Public endpoints:

- `GET /api/v1/health`
- `GET /api/v1/version`
- `POST /api/v1/ai/chat`
- `POST /api/v1/search/analyze`
- `GET /api/v1/search/stream/{requestId}`

The search pipeline performs language detection, intent analysis, policy evaluation, threat analysis, query rewrite, semantic analysis, AI decision making, execution-plan generation, execution, confidence scoring, and pipeline tracing.

## Request Identity

All public responses include or propagate a request identifier.

Clients may provide:

```http
X-Request-Id: client-generated-request-id
```

If absent, QwenBridge creates one internally. Use the same request id to correlate the REST response and SSE events.

## Analyze Search Query

### Request

```http
POST /api/v1/search/analyze
Content-Type: application/json
```

```json
{
  "requestId": "optional-client-request-id",
  "query": "wireless gaming mouse"
}
```

### cURL

```bash
curl -sS -X POST http://localhost:8080/api/v1/search/analyze \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: demo-request-1' \
  -d '{"requestId":"demo-request-1","query":"wireless gaming mouse"}'
```

## AI Chat

```http
POST /api/v1/ai/chat
Content-Type: application/json
```

```json
{
  "message": "Explain hybrid search in one sentence"
}
```

## Public Error Contract

All non-streaming public API failures use the `ApiError` envelope.

```json
{
  "timestamp": "2026-07-03T17:50:51Z",
  "status": 502,
  "error": "Bad Gateway",
  "code": "AI_PROVIDER_ERROR",
  "message": "AI provider failed",
  "path": "/api/v1/search/analyze",
  "requestId": "demo-request-1"
}
```

| HTTP status | Code | Meaning |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | Request DTO validation failed. |
| `400` | `BAD_REQUEST` | Malformed JSON or invalid request semantics. |
| `502` | `AI_PROVIDER_ERROR` | AI/Ollama provider failed or returned an invalid response. |
| `502` | `SEARCH_PROVIDER_ERROR` | Search/OpenSearch provider failed or timed out. |
| `500` | `INTERNAL_ERROR` | Unexpected server-side failure. |

## Safe Degradation Matrix

| Dependency failure | Public behavior | HTTP status |
| --- | --- | --- |
| Redis unavailable | Continue without distributed AI analysis cache. | `200` if the rest of the pipeline succeeds |
| OpenSearch unavailable | Return a controlled search-provider failure response. | `502 SEARCH_PROVIDER_ERROR` |
| Ollama unavailable | Return a controlled AI-provider failure response. | `502 AI_PROVIDER_ERROR` |
| SSE send failure or client disconnect | Close and remove the affected SSE session. If it was the final session for the request, cancel the AI token stream and skip caching its partial result. | Not applicable to the disconnected stream |
| AI stream duration, token, or event limit exceeded | Emit `ai.failed`, stop the AI stream, and continue with safe fallback analysis. | `200` if the pipeline otherwise succeeds |

## SSE Streaming

```http
GET /api/v1/search/stream/{requestId}
Accept: text/event-stream
```

Open the stream first:

```bash
curl -N http://localhost:8080/api/v1/search/stream/demo-request-1
```

Then submit the matching request:

```bash
curl -sS -X POST http://localhost:8080/api/v1/search/analyze \
  -H 'Content-Type: application/json' \
  -d '{"requestId":"demo-request-1","query":"wireless gaming mouse"}'
```

### Browser EventSource

```javascript
const requestId = crypto.randomUUID();
const source = new EventSource(`/api/v1/search/stream/${requestId}`);

source.addEventListener('stream.connected', event => {
  console.log('connected', JSON.parse(event.data));
});

source.addEventListener('ai.token', event => {
  const payload = JSON.parse(event.data);
  console.log(payload.content);
});

source.addEventListener('ai.completed', event => {
  console.log('AI completed', JSON.parse(event.data));
});

source.addEventListener('ai.failed', event => {
  console.warn('AI fallback', JSON.parse(event.data));
});

source.addEventListener('stream.failure', event => {
  console.error('stream failure', JSON.parse(event.data));
  source.close();
});

source.addEventListener('pipeline.completed', () => {
  source.close();
});

source.addEventListener('pipeline.failed', () => {
  source.close();
});
```

# REST API

## Overview

QwenBridge exposes a versioned public API for AI-native search analysis and
streaming pipeline events.

Current public API version: `v1`

Public endpoints:

- `GET /api/v1/health`
- `GET /api/v1/version`
- `POST /api/v1/ai/chat`
- `POST /api/v1/search/analyze`
- `GET /api/v1/search/stream/{requestId}`

The search pipeline performs:

- Language detection
- Intent analysis
- Policy evaluation
- Threat analysis
- Query rewrite
- Semantic analysis
- AI decision making
- Execution plan generation
- Execution engine execution
- Confidence scoring
- Pipeline tracing

---

## Request Identity

All public responses include or propagate a request identifier.

Clients may provide:

```http
X-Request-Id: client-generated-request-id
```

If the header is absent, QwenBridge creates a request id internally.

The same request id is used to correlate REST responses and SSE pipeline events.

---

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

### Successful Response

```http
HTTP/1.1 200 OK
Content-Type: application/json
```

```json
{
  "requestId": "demo-request-1",
  "processingTimeMs": 14,
  "originalQuery": "wireless gaming mouse",
  "language": "en",
  "intent": {},
  "rewrite": {},
  "semantic": {},
  "decision": {},
  "executionPlan": {
    "steps": [
      {
        "order": 1,
        "operation": "VECTOR_SEARCH",
        "description": "Search vector index"
      }
    ]
  },
  "executionResult": {
    "executed": true,
    "operations": ["VECTOR_SEARCH"],
    "results": [],
    "reason": "Execution plan executed successfully."
  },
  "confidence": {},
  "pipelineTrace": [],
  "cache": {}
}
```

---

## AI Chat

### Request

```http
POST /api/v1/ai/chat
Content-Type: application/json
```

```json
{
  "message": "Explain hybrid search in one sentence"
}
```

### cURL

```bash
curl -sS -X POST http://localhost:8080/api/v1/ai/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"Explain hybrid search in one sentence"}'
```

---

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

### Error Codes

| HTTP status | Code | Meaning |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | Request DTO validation failed. |
| `400` | `BAD_REQUEST` | Malformed JSON or invalid request semantics. |
| `502` | `AI_PROVIDER_ERROR` | AI/Ollama provider failed or returned an invalid response. |
| `502` | `SEARCH_PROVIDER_ERROR` | Search/OpenSearch provider failed or timed out. |
| `500` | `INTERNAL_ERROR` | Unexpected server-side failure. |

---

## Safe Degradation Matrix

| Dependency failure | Public behavior | HTTP status |
| --- | --- | --- |
| Redis unavailable | Continue without distributed AI analysis cache. The request must not fail only because Redis is unavailable. | `200` if the rest of the pipeline succeeds |
| OpenSearch unavailable | Return a controlled search-provider failure response. | `502 SEARCH_PROVIDER_ERROR` |
| Ollama unavailable | Return a controlled AI-provider failure response. | `502 AI_PROVIDER_ERROR` |
| SSE send failure / client disconnect | Close and remove the affected stream session. The pipeline request continues independently. | Not applicable to the disconnected stream |

---

## SSE Streaming

QwenBridge exposes request-scoped server-sent events.

```http
GET /api/v1/search/stream/{requestId}
Accept: text/event-stream
```

### cURL

```bash
curl -N http://localhost:8080/api/v1/search/stream/demo-request-1
```

Open the stream before submitting the matching analyze request:

```bash
curl -N http://localhost:8080/api/v1/search/stream/demo-request-1
```

In another terminal:

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

source.addEventListener('stream.failure', event => {
  console.error('stream failure', JSON.parse(event.data));
});

source.addEventListener('pipeline.completed', event => {
  console.log('pipeline completed', JSON.parse(event.data));
  source.close();
});
```

Full SSE contract details are documented in [`sse.md`](./sse.md).

---

## Compatibility Rules

Public API v1 follows additive compatibility:

- Adding optional fields is allowed.
- Removing fields is prohibited in v1.
- Renaming fields is prohibited in v1.
- Changing the type of an existing field is prohibited in v1.
- SSE event names are stable in v1.
- SSE payload type is stable per event in v1.
- Breaking changes require a new `/api/v2/...` endpoint family.

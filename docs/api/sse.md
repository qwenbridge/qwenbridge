# SSE Streaming API

## Overview

QwenBridge exposes request-scoped Server-Sent Events for pipeline lifecycle events and request-aware AI token streaming.

Endpoint:

```http
GET /api/v1/search/stream/{requestId}
Accept: text/event-stream
```

Open the stream before calling `POST /api/v1/search/analyze` with the same `requestId`.

## Events

### stream.connected

```json
{
  "requestId": "demo-request-1",
  "sessionId": "server-generated-session-id"
}
```

### ai.token

```json
{
  "requestId": "demo-request-1",
  "tokenIndex": 1,
  "content": "hello",
  "terminal": false
}
```

### ai.completed

```json
{
  "requestId": "demo-request-1",
  "tokenCount": 42,
  "terminal": false
}
```

### ai.failed

```json
{
  "requestId": "demo-request-1",
  "code": "AI_STREAM_LIMIT_EXCEEDED",
  "message": "AI streaming limit exceeded",
  "terminal": false
}
```

### stream.failure

```json
{
  "requestId": "demo-request-1",
  "code": "PIPELINE_FAILED",
  "message": "Pipeline failed before completion",
  "terminal": true
}
```

## Event Ordering

Successful stream:

```text
stream.connected
pipeline.started
ai.token*
ai.completed
pipeline.completed
```

Failed or degraded AI stream:

```text
stream.connected
pipeline.started
ai.token*
ai.failed
pipeline.completed | pipeline.failed
```

No `ai.token` is emitted after `ai.completed` or `ai.failed`.

## Cancellation

If one client disconnects, only that SSE session is removed.

If it was the last active SSE session for the request id, QwenBridge cancels the request-aware AI stream on a best-effort basis.

After cancellation:

- no more `ai.token` events are sent
- no `ai.completed` event is sent
- no `ai.failed` event is sent just because the client disconnected
- partial AI output is not cached
- the REST pipeline may continue with safe fallback behavior

## Safety Limits

```yaml
qwenbridge:
  streaming:
    session-timeout-ms: 300000
    max-ai-stream-duration: 30s
    max-ai-token-count: 1000
    max-ai-event-count: 1100
```

When a limit is exceeded, QwenBridge emits:

```text
event: ai.failed
```

with:

```json
{
  "code": "AI_STREAM_LIMIT_EXCEEDED"
}
```

## Browser EventSource Example

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

source.addEventListener('pipeline.completed', event => {
  console.log('pipeline completed', JSON.parse(event.data));
  source.close();
});

source.addEventListener('pipeline.failed', event => {
  console.error('pipeline failed', JSON.parse(event.data));
  source.close();
});
```

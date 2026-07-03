# SSE Streaming API

## Overview

QwenBridge uses request-scoped server-sent events to expose pipeline progress to
clients while a search request is being analyzed and executed.

Endpoint:

```http
GET /api/v1/search/stream/{requestId}
Accept: text/event-stream
```

The `{requestId}` path variable binds the stream to pipeline events with the same
request id.

---

## Public Event Envelope

Pipeline events use the stable `PipelineStreamingEvent` envelope.

```json
{
  "id": "event-id",
  "timestamp": "2026-07-03T17:50:51Z",
  "requestId": "demo-request-1",
  "event": "pipeline.completed",
  "stage": "pipeline",
  "type": "completed",
  "producer": "pipeline-engine",
  "sequenceNumber": 42,
  "payload": {}
}
```

### Envelope Fields

| Field | Type | Required | Stability |
| --- | --- | --- | --- |
| `id` | string | yes | Stable in v1 |
| `timestamp` | string, ISO-8601 | yes | Stable in v1 |
| `requestId` | string | yes | Stable in v1 |
| `event` | string | yes | Stable in v1 |
| `stage` | string | yes | Stable in v1 |
| `type` | string | yes | Stable in v1 |
| `producer` | string | yes | Stable in v1 |
| `sequenceNumber` | number | yes | Stable in v1 |
| `payload` | object or null | yes | Stable per event in v1 |

---

## Connection Event

When a stream is established, QwenBridge sends:

```text
event: stream.connected
```

Payload:

```json
{
  "requestId": "demo-request-1",
  "sessionId": "server-generated-session-id"
}
```

The connection event is not a pipeline event and does not use the full pipeline
event envelope.

---

## Failure Event

When the server can still write to the stream and needs to report a controlled
streaming failure, QwenBridge sends:

```text
event: stream.failure
```

Payload:

```json
{
  "requestId": "demo-request-1",
  "sessionId": "server-generated-session-id",
  "code": "STREAM_FAILURE",
  "message": "Streaming failure",
  "terminal": true
}
```

`stream.failure` is terminal for the affected SSE session.

---

## Pipeline Event Names

Pipeline event names use this format:

```text
{stage}.{type}
```

Examples:

- `pipeline.started`
- `pipeline.completed`
- `pipeline.failed`
- `pipeline.stopped`
- `execution.started`
- `execution.completed`

Terminal pipeline events:

- `pipeline.completed`
- `pipeline.failed`
- `pipeline.stopped`

When a terminal pipeline event is published, all stream sessions for the matching
request id are completed and removed.

---

## Compatibility Rules

SSE v1 compatibility is frozen by these rules:

- Existing event names are stable.
- Existing envelope fields must not be removed.
- Existing envelope field types must not change.
- Payload type is stable per event name.
- Optional additive fields are allowed.
- Breaking changes require a new `/api/v2/search/stream/{requestId}` endpoint.

---

## Client Disconnect Semantics

If the client disconnects, the server removes only the affected SSE session.

The search pipeline is not cancelled by default when a client disconnects. This
keeps REST request execution deterministic and prevents one disconnected UI tab
from cancelling work that may still be needed by another client.

If multiple stream sessions are attached to the same request id, a disconnect in
one session does not close the other sessions.

---

## Server Send Failure Semantics

If writing an event to an `SseEmitter` fails with `IOException` or
`IllegalStateException`, QwenBridge closes and removes that stream session.

The failure is treated as a stream lifecycle failure, not as a pipeline failure.

---

## JavaScript Example

```javascript
const requestId = crypto.randomUUID();
const source = new EventSource(`/api/v1/search/stream/${requestId}`);

source.addEventListener('stream.connected', event => {
  const payload = JSON.parse(event.data);
  console.log('connected', payload.sessionId);
});

source.addEventListener('stream.failure', event => {
  const payload = JSON.parse(event.data);
  console.error(payload.code, payload.message);
  source.close();
});

source.addEventListener('pipeline.completed', event => {
  const envelope = JSON.parse(event.data);
  console.log('done', envelope.sequenceNumber);
  source.close();
});
```

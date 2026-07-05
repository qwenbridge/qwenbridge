# Server-Sent Events API

## Endpoint

```text
GET /api/v1/search/stream/{requestId}
```

The stream is correlated to the original analysis request ID.

## Event model

QwenBridge emits a stable event envelope with an event name, event ID, timestamp, request ID, and typed payload.

Important event categories include:

- connection lifecycle
- pipeline snapshots
- AI token output
- AI completion
- AI failure
- terminal pipeline failure

Clients must handle unknown event types safely to remain forward-compatible.

## Terminal behavior

A stream ends after a terminal success or failure event. Consumers should not assume that every pipeline stage produces an event, and should treat disconnects as transport events rather than proof that the pipeline failed.

## Client support

Typed streaming clients are provided by the Java SDK and TypeScript SDK.

# ADR 0011: Public SSE Event Envelope

## Status

Accepted

## Context

QwenBridge exposes pipeline progress through server-sent events. Before V6, the
runtime could stream pipeline events, but the public event contract was not
explicitly frozen for external clients.

A public product launch requires stable event names, stable envelope fields,
well-defined failure semantics, and clear compatibility rules.

## Decision

QwenBridge will expose public pipeline events through a stable
`PipelineStreamingEvent` envelope in API v1.

The envelope contains:

- `id`
- `timestamp`
- `requestId`
- `event`
- `stage`
- `type`
- `producer`
- `sequenceNumber`
- `payload`

Event names use the `{stage}.{type}` format.

The initial connection event uses the event name `stream.connected` and a typed
connection payload. Controlled stream failures use `stream.failure` and a typed
failure payload.

The following compatibility rules apply to v1:

- Additive optional fields are allowed.
- Field removal is prohibited.
- Field renaming is prohibited.
- Existing field type changes are prohibited.
- Existing event names are stable.
- Payload type is stable per event name.
- Breaking changes require a new `/api/v2/...` endpoint family.

## Client Disconnect Semantics

Client disconnects close only the affected SSE session. They do not cancel the
underlying search pipeline by default.

This keeps REST execution deterministic and allows multiple clients to observe
the same request id independently.

## Consequences

Clients can safely build against the v1 SSE contract.

The server can add optional metadata over time without breaking existing clients.

Any breaking event or payload change must be introduced through a new versioned
API path.

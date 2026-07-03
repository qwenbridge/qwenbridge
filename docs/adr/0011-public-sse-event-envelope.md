# ADR 0011: Public SSE Event Envelope

## Status

Accepted

## Context

QwenBridge exposes pipeline progress through Server-Sent Events. Before V6, the runtime could stream pipeline events, but the public event contract was not explicitly frozen for external clients.

A public product launch requires stable event names, stable envelope fields, defined failure semantics, cancellation behavior, and clear compatibility rules.

## Decision

QwenBridge exposes public pipeline lifecycle events through the stable `PipelineStreamingEvent` envelope in API v1.

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

Pipeline event names use the `{stage}.{type}` format.

The initial connection event uses `stream.connected` with a typed connection payload. Controlled stream failures use `stream.failure` with a typed failure payload.

V7 adds request-aware AI streaming events:

- `ai.token`
- `ai.completed`
- `ai.failed`

AI events have typed payloads and are emitted before terminal pipeline lifecycle events. `ai.token` uses a strictly increasing token index. No token event may be emitted after `ai.completed` or `ai.failed`.

The following compatibility rules apply to v1:

- Additive optional fields are allowed.
- Field removal is prohibited.
- Field renaming is prohibited.
- Existing field type changes are prohibited.
- Existing event names are stable.
- Payload type is stable per event name.
- Breaking changes require a new `/api/v2/...` endpoint family.

## Client Disconnect Semantics

A client disconnect closes only its own SSE session.

If another session remains active for the same request id, AI token streaming continues.

If the disconnected session was the final active session, QwenBridge cancels the request-aware AI token stream on a best-effort basis. The server does not publish `ai.completed` or `ai.failed` merely because the client disconnected, and it does not cache the partial AI result.

The REST pipeline remains independently executable and may continue with safe fallback behavior.

## AI Stream Safety Limits

Request-aware AI streaming is bounded by configured limits:

- maximum SSE session duration
- maximum AI stream duration
- maximum AI token count
- maximum AI event count

A duration, token, or event limit breach emits `ai.failed` with `AI_STREAM_LIMIT_EXCEEDED`, stops AI streaming, and switches to safe fallback analysis.

## Consequences

Clients can safely build against the v1 SSE contract.

The server can add optional metadata over time without breaking existing clients.

Cancellation avoids unnecessary provider work after the final stream consumer disconnects while preserving independent REST pipeline behavior.

Any breaking event or payload change must be introduced through a new versioned API path.

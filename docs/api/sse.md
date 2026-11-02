# Server-Sent Events API

## v1: pipeline events

```text
GET /api/v1/search/stream/{requestId}
```

The stream is correlated to the original analysis request ID and follows ADR 0011:
- envelope `PipelineStreamingEvent`;
- `{stage}.{type}` event names;
- `stream.connected` / `stream.failure`;
- `ai.token` / `ai.completed` / `ai.failed`.

It is unchanged in 1.0.

## v2: progress events (`progress-event/2`)

```text
POST /api/v2/search/stream               (prefix "search")
POST /api/v2/sessions/{id}/turns/stream  (prefix "session")
```

One call, one finite stream. The SSE event name equals `kind`, and every `data:` line is one JSON
object:

```json
{
  "version": 2,
  "kind": "search.completed",
  "requestId": "r-42",
  "response": { "apiVersion": 2, "status": "OK" },
  "message": "",
  "sequence": 2,
  "terminal": true,
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
}
```

| Field | Meaning |
|---|---|
| `version` | Always `2` for this contract |
| `kind` | `<prefix>.started`, `<prefix>.completed` or `<prefix>.failed` |
| `requestId` | The request this stream belongs to (the ID to cancel) |
| `response` / `session` | The result, on `completed` only |
| `message` | Human-readable failure summary, on `failed` only |
| `sequence` | 1, 2, … in order of emission within the stream |
| `terminal` | `true` on the last event only |
| `error` | On `failed` only: `{code, category, message, retryable}`, as in [rest-api.md](rest-api.md) |
| `traceId` | The W3C trace ID of the request |

### Rules

- **Event order.** A stream has exactly one `started` event, then exactly one terminal event
  (`completed` or `failed`), and nothing after it.
- **Cancellation and timeout** are `failed` with `error.code` set to `CANCELLED` or `TIMEOUT`. No
  separate event kind exists, so 0.9-era clients that accept only these kinds keep working.
- **Heartbeats.** While the work runs, the server sends SSE comment lines (`:heartbeat`) every
  `qwenbridge.api.sse.heartbeat` (default 15 s). Clients ignore them.
  - A heartbeat that cannot be delivered means the client is gone, and the request is cancelled.
  - Disconnecting is therefore a way to cancel. `DELETE /api/v2/requests/{requestId}` also works.
- **Deadline.** `X-Request-Timeout-Ms` bounds the stream as it does the REST call.
- **Before the stream starts,** refusals (validation, authentication, capacity `429`) are ordinary
  `ApiError` responses.
- **Forward compatibility.** Clients must ignore unknown fields. `sequence`, `terminal`, `error` and
  `traceId` were added in 1.0; events from earlier servers lack them.

Fixtures parsed by both SDKs' tests, and checked against the server's real events:
- `qwenbridge-java-sdk/src/test/resources/contract/v2-search-stream-completed.sse`;
- `qwenbridge-java-sdk/src/test/resources/contract/v2-search-stream-failed.sse`.

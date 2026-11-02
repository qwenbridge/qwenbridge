# ADR 0040: Frontend-Neutral REST and SSE API

## Status

Accepted (QwenBridge 1.0, Section 3 Phase 26: QB-179 – QB-185)

## Context

The audit (QB-179 – QB-185) found the following:

- **Error responses.** v1 and v2 shared one error body, but v2 could not tell dependency failures,
  timeouts and unsupported capabilities apart from internal errors. The v1 refusal in authenticated
  deployments returned an ad hoc `{code, message}` body.
- **Streams.** v2 progress streams were synchronous. Disconnect did not stop work, and there were no
  cancelled or timeout semantics, no ordering field, no heartbeat and no trace ID.
- **Deadlines.** There were none. A request could hold a worker until each layer's own timeout
  passed (connectors: 65 s).
- **Trace context.** It stopped at the REST boundary; for SSE, its headers were never sent because
  the stream commits before response filters run. `X-Request-ID` was sent twice on every response.
- **Leaks.** Upstream REST errors reached clients with their status and internal client method
  names.
- **Contract tests.** No test noticed an added or removed endpoint.

## Decision

1. **Core request scope (QB-182, QB-184).** `io.qwenbridge.core.context.RequestScope` carries:
   - the request ID;
   - an optional validated `traceparent`;
   - an optional deadline;
   - a cancellation flag, shared with retrieval's `CancellationToken`.

   It is a thread-local that is never inherited implicitly. Work handed to another thread receives
   it through `propagate`. A closed scope is never current.
2. **Deadline-aware connector guard (QB-182).**
   - **Scope.** `Resilient` runs each attempt in the caller's scope.
   - **Timeout.** It bounds the attempt timeout by the time left.
   - **No late starts.** It starts no attempt once the request is cancelled or expired.
   - **Results.** It reports `CANCELLED` or `DEADLINE_EXCEEDED`; both are new and additive in
     `ConnectorResult.Failure`.
   - **Breaker.** Neither counts towards the circuit breaker; `CircuitBreaker.release()` frees a
     half-open trial without a verdict.
   - **Connector-level bounds.** The JDBC query timeout, the MongoDB `maxTime` and the Elasticsearch
     or OpenSearch HTTP timeout are each bounded by the deadline.
3. **API request lifecycle (QB-182, QB-183).** The following v2 endpoints run through
   `ApiRequests`:
   - search, refine and the search stream;
   - session turns and the session turn stream;
   - stateless conversation turns;
   - domain search.

   `ApiRequests` works as follows:
   - **Deadline.** It opens the scope with a deadline: `qwenbridge.api.request-timeout`, or the
     client's shorter `X-Request-Timeout-Ms`.
   - **Thread.** It keeps the work on the request thread, so request context survives.
   - **Stopping.** At the deadline, or on owner cancellation (`DELETE /api/v2/requests/{id}`), it
     interrupts that thread. The request then fails as `504 TIMEOUT` or `409 CANCELLED`, even when
     the work swallowed the interrupt and returned a partial result.
   - **Cleanup.** It clears the interrupt flag before the thread is reused.
   - **Uniqueness.** Request IDs are unique per tenant while in flight.
   - **Page size.** Pages above `qwenbridge.api.max-page-size` are refused.
   - **Checkpoints.** Commerce search checks the scope between its stages.
4. **Error model (QB-181).**
   - **Categories.** Each `ErrorCode` has an `ErrorCategory` and a `retryable` flag.
   - **New codes:** `CANCELLED`, `UNSUPPORTED_CAPABILITY`, `DEPENDENCY_UNAVAILABLE` and
     `TENANT_API_REQUIRED`.
   - **v2 bodies** add `category` and `retryable`, and map 501, 502/503 and 504 to specific codes.
   - **v1 bodies** keep exactly seven fields and their historical codes, because SDK 0.9.1 parses
     them strictly.
   - **v1 refusal.** The v1 refusal in authenticated deployments now uses `ApiError`.
   - **Upstream errors** are `502` with a generic message.
5. **SSE contract (QB-180).** v2 progress events gain `sequence`, `terminal`, `error` and `traceId`.
   - **No new event kinds.** Cancellation and timeout are `failed` with a typed `error`, because the
     0.9-era Java and TypeScript Commerce clients reject unknown kinds.
   - **Heartbeats** are SSE comments, ignored by every parser. A heartbeat that cannot be delivered
     cancels the request.
   - **Trace headers** are set before the stream commits.
6. **Trace propagation (QB-184).**
   - **Outbound headers.** Outbound OpenSearch and Ollama REST calls, and the Elasticsearch module,
     send `X-Request-ID`/`X-Opaque-Id` and a child `traceparent`.
   - **MongoDB** queries carry the request ID as their comment.
   - **Never forwarded:** caller headers.
   - **Response headers** are set once.
7. **Contract tests (QB-185).**
   - **Endpoint snapshot.** `ApiEndpointSnapshotTest` compares the served OpenAPI endpoints with a
     reviewed snapshot (60 endpoints).
   - **Shared fixtures.** v1 error and v2 stream fixtures are parsed by Java SDK tests (with a strict
     mapper) and TypeScript SDK tests. Server tests fail when a real server body has a field the
     fixtures lack.
   - **API tests** cover the error categories, the v1 shape, sequencing and terminal semantics,
     typed stream failures, the timeout and cancellation paths end to end (the blocked backend call
     is interrupted), and owner-only cancellation.
   - **Unit tests** (Core and server) cover the scope, the guard and `ApiRequests`.

## Consequences

- **Behaviour changes within v2:**
  - a search `requestId` must satisfy the request-ID rule (400 otherwise);
  - a concurrent duplicate request ID is `409`;
  - upstream REST errors are `502 DEPENDENCY_UNAVAILABLE` (on v1 as well: `502 INTERNAL_ERROR`);
  - requests are bounded by 60 s by default.
- **Bug fix.** The v2 search stream used to drop a request's `contract` field; it is now kept.
- **Limitations.**
  - Blocking calls that ignore interrupts (some driver internals) stop only at their own timeouts,
    which are now bounded by the deadline.
  - The v1 pipeline, AI token streaming and asynchronous pipeline work are not yet under the
    request scope; their own limits (ADR 0011) apply.
  - PostgreSQL statements carry no request ID.
- **SDKs.** The SDKs do not yet expose `X-Request-Timeout-Ms`, cancellation or the typed v2 error;
  that is Phase 27 (QB-186 – QB-192).

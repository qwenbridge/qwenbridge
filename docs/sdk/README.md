# QwenBridge SDKs

Two maintained client libraries implement the public API ([REST](../api/rest-api.md),
[SSE](../api/sse.md)). Neither needs server classes, a database or a frontend framework.

| | Java (`io.qwenbridge:qwenbridge-java-sdk`) | TypeScript (`@qwenbridge/sdk`) |
|---|---|---|
| Runtime | Java 25 (the build's release level; `java.net.http`) | Node.js 20+ and current browsers (`fetch`, streams) |
| v1 analyze and pipeline SSE | `QwenBridgeClient`, `QwenBridgeStreamingClient` | `QwenBridgeClient`, `QwenBridgeStreamingClient` |
| v2 search, sessions, streams | `commerce.CommerceClient` | `CommerceClient` |
| Web Component | — | separate entry point `@qwenbridge/sdk/web-component` ([docs](web-component.md)) |

## The v2 client (1.0)

| Concern | Behaviour |
|---|---|
| Errors | `QwenBridgeApiException` / `QwenBridgeApiError` with `status`, `code`, `category`, `retryable`. Transport failures and timeouts throw `QwenBridgeTransportException` / `QwenBridgeTransportError`. A response of an unsupported contract version throws `QwenBridgeIncompatibleServerException` / `…Error` instead of being mapped silently. |
| Timeouts | One per call (default 120 s), also sent as `X-Request-Timeout-Ms` so the server stops its work at the same deadline. |
| Retries | Bounded exponential backoff, only for safe operations: search, refine and reads. Every attempt reuses the same request ID. Session turns, acceptances, session creation, streams and cancellations are never retried. A failure is retried when the server marks it `retryable`, or (v1) on 429/502/503/504. |
| Cancellation | `cancel(requestId)` calls `DELETE /api/v2/requests/{id}`. Cancelling a Java `searchAsync`/`streamAsync` future, or aborting a TypeScript `AbortSignal`, also asks the server to cancel. |
| Streams | `Accept: text/event-stream`. Heartbeat comments are ignored. Each stream ends at the first terminal event (`terminal`, or `.completed`/`.failed` from older servers). Out-of-order `sequence`, unknown kinds or versions, and streams without a terminal event are errors. A `*.failed` event carries a typed `error` (`CANCELLED`, `TIMEOUT`, …). |
| Version | `version()` returns `GET /api/v2/version`; `requireCompatibleServer()` fails unless the server implements `v2` with `progress-event/2` and `api-error/2`. |
| Models | Typed records/interfaces for the whole search response, including the 1.0 `contract`, `retrieval`, `interpretation`, `subqueries` and `clarifications`. `Request.planned(query)` / `contract: COMMERCE_CONTRACT_V1` selects the planned Commerce Search Contract. |
| Credentials | Bearer tokens stay in headers. They never appear in exceptions or in `toString`. |

## How compatibility is tested

- **Response models.** Real server responses are captured by the server's `CommerceApiTest` into
  `qwenbridge-java-sdk/src/test/resources/contract/`. That test fails when the server returns a
  field the fixtures lack. The SDK tests (`CommerceModelCoverageTest`, `commerce-resilience.test.ts`)
  fail when a fixture field has no typed model.
- **Streams and errors.** The shared fixtures cover the v1 error body (parsed with the strict type
  released in 0.9.1) and v2 streams with the 1.0 fields, heartbeats and typed failures. Both SDKs
  parse them.
- **Live checks.** The master verification runs both v2 clients against the Docker server:
  - compatibility check;
  - Unicode search;
  - stream with the 1.0 fields;
  - typed validation error;
  - cancellation.

  It also runs the built TypeScript SDK on Node.js 20 in an offline container. The v1 analyze path
  has its own live Java and TypeScript checks.

## Changes from 0.9

- **Additive.** The 0.9 constructors of the Java records are kept.
- **`QwenBridgeApiError`** now ignores unknown fields and adds `category` and `retryable`.
- **v2 client (Java).** Its HTTP failures used to throw `IllegalStateException` (no body). They now
  throw `QwenBridgeApiException`; transport failures throw `QwenBridgeTransportException`.
- **v2 client (TypeScript).** Plain `Error`s become `QwenBridgeApiError` / `QwenBridgeTransportError`,
  which still extend `Error`.
- **TypeScript `CommerceRequest.backend`** is any configured connector ID, not a fixed union.

See also: [historical Spring Boot Starter](spring-boot-starter.md).

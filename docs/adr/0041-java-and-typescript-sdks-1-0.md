# ADR 0041: Java and TypeScript SDKs for 1.0

## Status

Accepted (QwenBridge 1.0, Section 3 Phase 27: QB-186 – QB-192)

## Context

Both SDKs had a typed, retrying v1 client (`QwenBridgeClient`). Their v2 Commerce clients were
thinner:

- **Errors.** HTTP failures were untyped: Java threw `IllegalStateException` without the body;
  TypeScript threw a plain `Error`.
- **Timeouts, retries and cancellation.** There was no configurable timeout or retry, and no
  server-side cancellation.
- **Models.** They lacked the 1.0 response sections (`contract`, `retrieval`, `interpretation`,
  `subqueries`, `clarifications`), the 1.0 event fields and several returned fields. Unknown fields
  were ignored silently, so nothing showed what was dropped.
- **Spring Boot Starter.** It was removed in the Quarkus migration, with only one-line mentions left.

## Decision

1. **Typed failures (QB-186, QB-187).**
   - **Server errors.** `QwenBridgeApiError` (both SDKs) adds `category` and `retryable` and
     ignores unknown fields; the 0.9 constructor is kept.
   - **Transport.** Network failures and timeouts are transport errors.
   - **Incompatible servers.** Unsupported `apiVersion`, event versions or kinds, out-of-order
     sequences, and a server without the `progress-event/2` and `api-error/2` contracts raise
     `QwenBridgeIncompatibleServer{Exception,Error}`.
2. **Timeouts, retries, cancellation (QB-190).**
   - **Timeout.** Each call has one (Java `CommerceClientConfig`, TypeScript `timeoutMs`), sent as
     `X-Request-Timeout-Ms`.
   - **Retries.** Only safe operations are retried (search, refine, reads), with exponential backoff
     and the request ID assigned before the first attempt. A failure is retried when the server marks
     it `retryable`, or (v1) on 429/502/503/504. Turns, acceptances, session creation, streams and
     cancellations are attempted once.
   - **Cancellation.** `cancel(requestId)` calls the server. Cancelling a Java future, or aborting a
     TypeScript signal, also cancels on the server.
3. **REST and SSE (QB-188).**
   - **Accept header.** Streams send `Accept: text/event-stream`; the live checks found that
     `application/json` is refused with 406.
   - **Comments** are ignored.
   - **End of stream.** Each stream ends at the first terminal event; extra events are not read.
   - **Errors.** Failed events carry the typed `error`.
4. **Models (QB-189).** Maintained models, checked against real responses, are preferred over code
   generation. The server writes real responses to fixtures and fails when it returns a field the
   fixtures lack; the SDK tests fail when a fixture field has no typed model. TypeScript keeps
   `COMMERCE_RESPONSE_FIELDS` equal to the `CommerceResponse` type at compile time.
   - **New models:** `Retrieval`, `Interpretation`, `SubQuery`, `Clarification`, `FieldSpec`, hit
     `metadata` and `results.empty`.
   - **Planned requests:** `Request.planned` / `COMMERCE_CONTRACT_V1`.
5. **Compatibility tests (QB-191).**
   - **Unit tests,** against scripted servers: Java `CommerceClientResilienceTest` (6) and
     `CommerceModelCoverageTest` (2); TypeScript `commerce-resilience.test.ts` (7).
   - **Shared fixtures.** The v1 error is parsed with the type released in 0.9.1.
   - **Three new master steps:** the Java and TypeScript v2 clients against the live server, and
     the built TypeScript SDK on Node.js 20 (offline container, read-only mount).
6. **Spring Boot Starter (QB-192).** `docs/sdk/spring-boot-starter.md` records:
   - its history (v0.9.0 – v0.9.1, Spring Boot 3.5, v1 only);
   - that it is discontinued and not reintroduced;
   - the recommended path: plain SDK beans.

## Consequences

- **Additive for 0.9 callers.** Existing record constructors are kept.
- **Different failure types.** Callers of the v2 clients now get typed errors instead of
  `IllegalStateException` / plain `Error`.
- **Live dependency.** The master gate now needs the `node:20-alpine` image; the first run pulls it.
- **Java requirement.** The Java SDK requires Java 25, the build's release level.
- **Not covered.** The v1 clients still have no server-side cancellation, because v1 has none
  (ADR 0040).

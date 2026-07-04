# V9 Release Evidence

## Release objective

V9 delivers the first QwenBridge developer platform surface: a Java SDK for the Search Analyze API, resilient sync and async client calls, runnable examples, and developer documentation.

## Repository structure

The repository is organized as a Maven multi-module build:

- `qwenbridge-server` — Spring Boot API and search pipeline.
- `qwenbridge-java-sdk` — first-party Java SDK.
- `examples/java-sdk-example` — runnable SDK usage examples.

## SDK capabilities verified

### Client APIs

- Synchronous Search Analyze calls through `QwenBridgeClient.analyze`.
- Asynchronous Search Analyze calls through `QwenBridgeClient.analyzeAsync`.
- Configurable base URL, connect timeout, and request timeout.
- Local default client configuration.
- Request ID propagation through `X-Request-Id`.

### Error behavior

- Successful HTTP responses map to SDK response models.
- Non-success API responses map to `QwenBridgeApiException`.
- Structured server error payloads are available through `apiError()`.
- Transport failures map to `QwenBridgeTransportException`.
- Interrupted synchronous requests preserve the interrupted-thread state.

### Retry behavior

- Retry policy configuration is implemented.
- Retry classification covers transient HTTP and transport failures.
- Retryable HTTP statuses include `408`, `429`, `500`, `502`, `503`, and `504`.
- Exponential backoff is implemented.
- Sync and async paths retry retryable failures.
- Non-retryable API errors return without retry.

## Automated verification

The V9 implementation was verified through the Maven reactor:

- Server test suite: passed.
- Java SDK test suite: passed.
- Java SDK examples module: compiled successfully.
- Full Maven reactor build: passed.

Latest verified build summary:

    QwenBridge Parent ................ SUCCESS
    QwenBridge ........................ SUCCESS
    QwenBridge Java SDK ............... SUCCESS
    QwenBridge Java SDK Example ....... SUCCESS
    BUILD SUCCESS

## Test coverage evidence

Java SDK coverage includes:

- Successful synchronous response mapping.
- Successful asynchronous response mapping.
- Request ID header propagation.
- API error mapping.
- Transport failure mapping.
- Blank-query validation before HTTP execution.
- Retry policy validation.
- Retry classification.
- Exponential backoff calculation.
- Sync retry after transient `503`.
- Async retry after transient `503`.
- Non-retryable failure behavior.
- Interrupted request behavior.

## Documentation evidence

Developer-facing documentation is available in:

- `qwenbridge-java-sdk/README.md`
- `examples/java-sdk-example`
- Root `README.md`

## Remaining release actions

Before final V9 release:

- Open and review the pull request.
- Merge `feat/v9-developer-platform` into `main`.
- Create the annotated V9 release tag.
- Publish release notes.

## Java SDK SSE Streaming Evidence

Delivered implementation:

- QwenBridgeStreamingClient consumes GET /api/v1/search/stream/{requestId}
- stream(requestId, handler) delivers raw SSE events
- streamTyped(requestId, handler) delivers typed SDK payloads

Typed payload coverage:

- stream.connected -> ConnectedStreamingPayload
- ai.token -> AITokenStreamingPayload
- ai.completed -> AICompletedStreamingPayload
- ai.failed -> AIFailedStreamingPayload
- unknown or malformed event -> UnknownStreamingPayload

Source evidence:

- qwenbridge-java-sdk/src/main/java/io/qwenbridge/sdk/streaming/QwenBridgeStreamingClient.java
- qwenbridge-java-sdk/src/main/java/io/qwenbridge/sdk/streaming/StreamingPayloadMapper.java
- qwenbridge-java-sdk/src/main/java/io/qwenbridge/sdk/streaming/TypedStreamingEvent.java
- qwenbridge-java-sdk/src/main/java/io/qwenbridge/sdk/streaming/TypedStreamingEventHandler.java
- qwenbridge-java-sdk/src/main/java/io/qwenbridge/sdk/streaming/payload/
- qwenbridge-java-sdk/src/test/java/io/qwenbridge/sdk/streaming/QwenBridgeStreamingClientTest.java
- qwenbridge-java-sdk/src/test/java/io/qwenbridge/sdk/streaming/TypedStreamingPayloadTest.java
- examples/java-sdk-example/src/main/java/io/qwenbridge/examples/TypedStreamingExample.java
- qwenbridge-java-sdk/README.md

Verification evidence:

- QwenBridge server tests: 319 passed
- QwenBridge Java SDK tests: 35 passed
- Java SDK example module: compiled successfully
- Maven reactor result: BUILD SUCCESS

Relevant commits:

- a3a2a83 feat(java-sdk): add SSE streaming client foundation
- e7f208a feat(java-sdk): map typed SSE streaming payloads
- c5837e0 docs(java-sdk): add typed SSE streaming example

Scope boundary:

This evidence confirms SDK-side SSE consumption and typed payload mapping against the existing server stream contract. It does not claim that the server already performs live token-by-token AI generation.

## Additional V9 evidence: Spring Boot Starter and TypeScript SDK

### Spring Boot Starter

Implemented commits:

- `bf3c02e` - `feat(starter): add Spring Boot starter module skeleton`
- `66b6ef2` - `fix(starter): correct properties source syntax`
- `3842aba` - `feat(starter): auto-configure QwenBridge SDK clients`
- `f8b0e21` - `feat(starter): add configuration health indicator`
- `8d117f9` - `docs(starter): add usage documentation and sample app`

Evidence:

- Reactor build succeeded with Spring Boot starter module included.
- Starter tests passed.
- Starter sample app compiled.
- Auto-configuration covers `QwenBridgeClient`.
- Auto-configuration covers `QwenBridgeStreamingClient`.
- Health indicator is available when actuator health classes are present.

### TypeScript SDK

Implemented commits:

- `2e49e50` - `feat(typescript-sdk): add package foundation`
- `1262a6f` - `feat(typescript-sdk): add fetch analyze client`
- `10ad165` - `feat(typescript-sdk): add retry policy`
- `4e8dc15` - `feat(typescript-sdk): add SSE streaming client foundation`
- `c40a189` - `feat(typescript-sdk): map typed SSE payloads`
- `54b14f6` - `docs(typescript-sdk): add usage examples`
- `5f85c8b` - `docs(typescript-sdk): prepare package publishing`

Verification evidence:

- `npm --prefix qwenbridge-typescript-sdk install` succeeded.
- `npm --prefix qwenbridge-typescript-sdk run build` succeeded.
- `npm --prefix qwenbridge-typescript-sdk test` succeeded.
- Vitest result: 6 test files passed.
- Vitest result: 31 tests passed.
- `npm --prefix qwenbridge-typescript-sdk run pack:check` succeeded.
- npm dry-run package contents were reviewed.
- npm audit result: 0 vulnerabilities.

TypeScript SDK capabilities verified:

- Fetch-based `analyze()` client
- Request id header propagation
- Typed API errors
- Transport error wrapping
- Retry policy
- Retry classifier
- Retry executor
- Raw SSE parsing
- Typed SSE payload mapping
- Usage examples
- Publish metadata
- Publishing guide

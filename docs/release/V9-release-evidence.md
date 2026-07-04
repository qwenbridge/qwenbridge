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

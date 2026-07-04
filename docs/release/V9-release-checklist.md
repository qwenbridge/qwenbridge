# V9 Release Checklist

## Repository and build

- [x] Repository converted to Maven multi-module structure.
- [x] Server module is located in `qwenbridge-server`.
- [x] Java SDK module is located in `qwenbridge-java-sdk`.
- [x] Java SDK examples module is located in `examples/java-sdk-example`.
- [x] Full Maven reactor build succeeds.
- [x] Working tree is clean after committed work.

## Java SDK API

- [x] `QwenBridgeClient` is available.
- [x] Synchronous `analyze` API is available.
- [x] Asynchronous `analyzeAsync` API is available.
- [x] `QwenBridgeClientConfig` supports base URL and timeout configuration.
- [x] `QwenBridgeClient.localDefault()` is available.
- [x] `SearchAnalyzeRequest` validates blank queries before HTTP execution.
- [x] Request IDs are propagated through `X-Request-Id`.

## Error handling

- [x] Successful API responses map to `SearchAnalyzeResponse`.
- [x] Structured API errors map to `QwenBridgeApiException`.
- [x] API error details are accessible through `apiError()`.
- [x] Transport failures map to `QwenBridgeTransportException`.
- [x] Interrupted synchronous calls restore the interrupted-thread state.

## Retry behavior

- [x] Retry policy configuration is available.
- [x] Retryable HTTP statuses are classified.
- [x] Retryable transport failures are classified.
- [x] Exponential backoff is implemented.
- [x] Synchronous calls retry retryable failures.
- [x] Asynchronous calls retry retryable failures.
- [x] Non-retryable API failures are not retried.
- [x] Retry behavior is covered by automated tests.

## Documentation and examples

- [x] SDK README exists.
- [x] README documents synchronous usage.
- [x] README documents asynchronous usage.
- [x] README documents request IDs.
- [x] README documents retry behavior.
- [x] README documents SDK exceptions.
- [x] Runnable sync example exists.
- [x] Runnable async example exists.
- [x] Root README links to SDK documentation and examples.

## Release gate

- [x] Server tests pass.
- [x] SDK tests pass.
- [x] Example module compiles.
- [x] Full Maven reactor build passes.
- [ ] Pull request reviewed and merged.
- [ ] V9 release tag created.

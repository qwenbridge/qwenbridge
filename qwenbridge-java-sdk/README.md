# QwenBridge Java SDK

Official Java client for the QwenBridge public API.

The SDK supports synchronous search analysis, asynchronous search analysis, typed API errors, retry handling, and typed SSE streaming.

## Requirements

- Java 25
- Maven 3.9+
- A running QwenBridge server

## Installation

Local monorepo development:

```xml
<dependency>
    <groupId>io.qwenbridge</groupId>
    <artifactId>qwenbridge-java-sdk</artifactId>
    <version>1.0.0-RC1</version>
</dependency>
```

Before Maven Central publishing, install locally:

```bash
mvn -pl qwenbridge-java-sdk install
```

## Create a client

```java
import io.qwenbridge.sdk.QwenBridgeClient;
import io.qwenbridge.sdk.config.QwenBridgeClientConfig;

import java.net.URI;
import java.time.Duration;

QwenBridgeClient client = new QwenBridgeClient(new QwenBridgeClientConfig(
        URI.create("http://localhost:8080"),
        Duration.ofSeconds(2),
        Duration.ofSeconds(30)
));
```

For local development:

```java
QwenBridgeClient client = QwenBridgeClient.localDefault();
```

## Synchronous analysis

```java
import io.qwenbridge.sdk.search.SearchAnalyzeRequest;
import io.qwenbridge.sdk.search.SearchAnalyzeResponse;

SearchAnalyzeResponse response = client.analyze(
        SearchAnalyzeRequest.withRequestId(
                "request-123",
                "best laptop for software development"
        )
);

System.out.println(response.intent());
System.out.println(response.decision());
System.out.println(response.confidence());
```

## Asynchronous analysis

```java
client.analyzeAsync(
        SearchAnalyzeRequest.withRequestId(
                "request-456",
                "best wireless headphones"
        )
).thenAccept(response -> {
    System.out.println(response.intent());
    System.out.println(response.decision());
    System.out.println(response.confidence());
}).join();
```

## Request IDs

A request ID correlates SDK calls with server logs, pipeline events, SSE streams, and API error responses.

```java
SearchAnalyzeRequest request = SearchAnalyzeRequest.withRequestId(
        "checkout-search-8f9f0d",
        "wireless noise cancelling headphones"
);
```

The SDK sends this value as the `X-Request-Id` HTTP header.

## Retry behavior

The SDK retries transient failures according to the configured retry policy.

Retryable failures include:

- HTTP `408`
- HTTP `429`
- HTTP `500`
- HTTP `502`
- HTTP `503`
- HTTP `504`
- transport failures such as connection failures and timeouts

Non-retryable API failures, such as validation errors and authorization failures, are returned immediately.

## Exceptions

`QwenBridgeApiException` is thrown when QwenBridge returns a non-success HTTP response.

```java
try {
    client.analyze(SearchAnalyzeRequest.of("iphone"));
} catch (QwenBridgeApiException exception) {
    System.out.println(exception.apiError().status());
    System.out.println(exception.apiError().code());
    System.out.println(exception.apiError().message());
    System.out.println(exception.apiError().requestId());
}
```

`QwenBridgeTransportException` is thrown when the SDK cannot communicate with QwenBridge due to a transport-level failure.

## Typed SSE streaming

```java
import io.qwenbridge.sdk.streaming.QwenBridgeStreamingClient;
import io.qwenbridge.sdk.streaming.payload.AITokenStreamingPayload;

QwenBridgeStreamingClient streamingClient = new QwenBridgeStreamingClient(
        new QwenBridgeClientConfig(
                URI.create("http://localhost:8080"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(30)
        )
);

streamingClient.streamTyped("request-123", event -> {
    if (event.payload() instanceof AITokenStreamingPayload token) {
        System.out.print(token.content());
    }
}).join();
```

Typed payloads include:

- `ConnectedStreamingPayload`
- `AITokenStreamingPayload`
- `AICompletedStreamingPayload`
- `AIFailedStreamingPayload`
- `UnknownStreamingPayload`

## Examples

The repository's independent Java consumer,
[`e2e/dual-sdk/java-consumer`](../e2e/dual-sdk/java-consumer), is a runnable, plain Java 25
application. It uses this SDK as an external Maven dependency and covers:
- v2 search;
- streaming and cancellation;
- typed errors;
- the v1 analyze calls (synchronous and asynchronous);
- typed v1 streaming.

The repository README has a short quick start.

### Persistent commerce sessions

The Java 25 `CommerceClient` supports `createSession`, `session`, `turn`,
`streamTurn`, `proposals`, and `accept`. Typed responses expose paging cursors,
actual stage timings, policy decisions and locked fields. Legacy search/refine
constructors remain available. Include the current session revision for turns
and consent; retain a session ID to reconnect with the same authenticated owner.
See [Table 1 contracts](../docs/platform/table-1-api.md).

### v2 client: errors, timeouts, retries and cancellation (1.0)

```java
var config = new CommerceClientConfig(
    URI.create("https://search.example.com"), apiKey,
    Duration.ofSeconds(5),      // connect
    Duration.ofSeconds(30),     // per call; also sent as X-Request-Timeout-Ms
    RetryPolicy.defaultPolicy());
try (var client = new CommerceClient(config)) {
  client.requireCompatibleServer();
  var pending = client.searchAsync(CommerceContracts.Request.planned("black shoes under 100 EUR"));
  pending.cancel(true);            // aborts the call and asks the server to cancel
} catch (QwenBridgeApiException e) {
  // e.status(), e.code(), e.category(), e.apiError().retryable()
}
```

Only search, refine and reads are retried, always with the same request ID. Session turns,
acceptances, streams and cancellations are never retried. See [docs/sdk](../docs/sdk/README.md) for
the full behaviour and how compatibility is tested.

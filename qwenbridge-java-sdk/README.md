# QwenBridge Java SDK

Official Java client for the QwenBridge public API.

The SDK supports synchronous search analysis, asynchronous search analysis, typed API errors, retry handling, and typed SSE streaming.

## Requirements

- Java 21+
- Maven 3.9+
- A running QwenBridge server

## Installation

Local monorepo development:

```xml
<dependency>
    <groupId>io.qwenbridge</groupId>
    <artifactId>qwenbridge-java-sdk</artifactId>
    <version>0.9.0</version>
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

Runnable examples are available in:

```text
examples/java-sdk-example
```

Example guide:

```text
docs/examples/java-sdk-example.md
```

# QwenBridge Java SDK

Official Java client for the QwenBridge Search Analyze API.

## Requirements

- Java 21+
- A running QwenBridge server

## Installation

For local development inside the QwenBridge monorepo:

    <dependency>
        <groupId>io.qwenbridge</groupId>
        <artifactId>qwenbridge-java-sdk</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </dependency>

## Synchronous usage

    import io.qwenbridge.sdk.QwenBridgeClient;
    import io.qwenbridge.sdk.config.QwenBridgeClientConfig;
    import io.qwenbridge.sdk.search.SearchAnalyzeRequest;
    import io.qwenbridge.sdk.search.SearchAnalyzeResponse;

    import java.net.URI;
    import java.time.Duration;
    import java.util.UUID;

    QwenBridgeClient client = new QwenBridgeClient(new QwenBridgeClientConfig(
            URI.create("http://localhost:8080"),
            Duration.ofSeconds(2),
            Duration.ofSeconds(30)
    ));

    SearchAnalyzeResponse response = client.analyze(
            SearchAnalyzeRequest.withRequestId(
                    UUID.randomUUID().toString(),
                    "best mechanical keyboard"
            )
    );

    System.out.println(response.intent());
    System.out.println(response.decision());
    System.out.println(response.confidence());

## Asynchronous usage

    import io.qwenbridge.sdk.QwenBridgeClient;
    import io.qwenbridge.sdk.config.QwenBridgeClientConfig;
    import io.qwenbridge.sdk.search.SearchAnalyzeRequest;

    import java.net.URI;
    import java.time.Duration;
    import java.util.UUID;

    QwenBridgeClient client = new QwenBridgeClient(new QwenBridgeClientConfig(
            URI.create("http://localhost:8080"),
            Duration.ofSeconds(2),
            Duration.ofSeconds(30)
    ));

    client.analyzeAsync(
                    SearchAnalyzeRequest.withRequestId(
                            UUID.randomUUID().toString(),
                            "best gaming monitor"
                    )
            )
            .thenAccept(response -> {
                System.out.println(response.intent());
                System.out.println(response.decision());
                System.out.println(response.confidence());
            })
            .join();

## Local default client

For a QwenBridge server running locally on the default address:

    QwenBridgeClient client = QwenBridgeClient.localDefault();

## Request IDs

Use a request ID to correlate SDK calls with server logs, pipeline events, and API error responses.

    SearchAnalyzeRequest request = SearchAnalyzeRequest.withRequestId(
            "checkout-search-8f9f0d",
            "wireless noise cancelling headphones"
    );

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
- Transport failures such as connection failures and timeouts

The retry mechanism uses exponential backoff. Non-retryable API failures, such as validation errors (`400`) and authorization failures (`401` / `403`), are returned immediately.

## Exceptions

### QwenBridgeApiException

Thrown when QwenBridge returns a non-success HTTP response. The structured API error is available through `apiError()`.

    try {
        client.analyze(SearchAnalyzeRequest.of("iphone"));
    } catch (QwenBridgeApiException exception) {
        System.out.println(exception.apiError().status());
        System.out.println(exception.apiError().code());
        System.out.println(exception.apiError().message());
        System.out.println(exception.apiError().requestId());
    }

### QwenBridgeTransportException

Thrown when the SDK cannot communicate with QwenBridge due to a transport-level failure, such as connection refusal, timeout, interrupted request, or an invalid response body.

## Examples

Runnable examples are available in:

    examples/java-sdk-example

- `SyncSearchAnalyzeExample`
- `AsyncSearchAnalyzeExample`

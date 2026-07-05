# QwenBridge Spring Boot Starter

Spring Boot auto-configuration for the QwenBridge Java SDK.

The starter creates ready-to-use QwenBridge client beans and optional Actuator health integration.

## Requirements

- Java 21+
- Spring Boot 3.5+
- QwenBridge Java SDK
- Optional: Spring Boot Actuator

## Installation

```xml
<dependency>
    <groupId>io.qwenbridge</groupId>
    <artifactId>qwenbridge-spring-boot-starter</artifactId>
    <version>0.9.0</version>
</dependency>
```

Before Maven Central publishing, install locally:

```bash
mvn -pl qwenbridge-java-sdk,qwenbridge-spring-boot-starter install
```

## Configuration

```yaml
qwenbridge:
  base-url: http://localhost:8080
  connect-timeout: 2s
  request-timeout: 30s
```

## Auto-configured beans

The starter creates these beans automatically:

- `QwenBridgeClientConfig`
- `QwenBridgeClient`
- `QwenBridgeStreamingClient`

All beans use `ConditionalOnMissingBean`, so applications can override them by defining their own beans.

## Search Analyze usage

```java
@RestController
class SearchController {

    private final QwenBridgeClient client;

    SearchController(QwenBridgeClient client) {
        this.client = client;
    }

    @GetMapping("/search")
    SearchAnalyzeResponse search(@RequestParam String query) {
        return client.analyze(SearchAnalyzeRequest.of(query));
    }
}
```

## Streaming usage

```java
@Service
class StreamingService {

    private final QwenBridgeStreamingClient streamingClient;

    StreamingService(QwenBridgeStreamingClient streamingClient) {
        this.streamingClient = streamingClient;
    }

    void stream(String requestId) {
        streamingClient.streamTyped(requestId, event -> {
            System.out.println(event.eventName());
        }).join();
    }
}
```

## Actuator health

When Spring Boot Actuator is present, the starter exposes a configuration-only health indicator.

The indicator reports:

- `UP` when `qwenbridge.base-url` is configured
- `DOWN` when `qwenbridge.base-url` is blank

The health check does not call the remote QwenBridge server. This keeps `/actuator/health` independent from remote network latency and remote availability.

## Example application

A sample Spring Boot application is available at:

```text
examples/spring-boot-starter-example
```

Example guide:

```text
docs/examples/spring-boot-starter-example.md
```

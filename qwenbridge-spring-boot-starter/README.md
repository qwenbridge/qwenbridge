# QwenBridge Spring Boot Starter

Spring Boot auto-configuration for the QwenBridge Java SDK.

## Requirements

- Java 21+
- Spring Boot 3.5+
- QwenBridge Java SDK
- Optional: Spring Boot Actuator for health integration

## Installation

For local development inside the QwenBridge monorepo:

    <dependency>
        <groupId>io.qwenbridge</groupId>
        <artifactId>qwenbridge-spring-boot-starter</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </dependency>

## Configuration

    qwenbridge:
      base-url: http://localhost:8080
      connect-timeout: 2s
      request-timeout: 30s

## Auto-configured beans

The starter creates these beans automatically:

- QwenBridgeClientConfig
- QwenBridgeClient
- QwenBridgeStreamingClient

All beans use ConditionalOnMissingBean, so applications can override them by defining their own beans.

## Search Analyze usage

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

## Streaming usage

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

## Actuator health

When Spring Boot Actuator is present, the starter exposes a configuration-only HealthIndicator.

The indicator reports:

- UP when qwenbridge.base-url is configured
- DOWN when qwenbridge.base-url is blank

The health check does not call the remote QwenBridge server. This avoids making /actuator/health dependent on network latency or remote availability.

Example:

    GET /actuator/health

Expected detail:

    qwenBridgeHealthIndicator:
      status: UP
      details:
        baseUrl: http://localhost:8080
        mode: configuration-only

## Example application

A sample Spring Boot application is available at:

    examples/spring-boot-starter-example

Run it with:

    mvn -pl examples/spring-boot-starter-example spring-boot:run

Useful endpoints:

    GET http://localhost:8081/example/beans
    GET http://localhost:8081/example/analyze\?query\=iphone
    GET http://localhost:8081/actuator/health

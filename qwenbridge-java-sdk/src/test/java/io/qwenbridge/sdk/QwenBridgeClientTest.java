package io.qwenbridge.sdk;

import com.sun.net.httpserver.HttpServer;
import io.qwenbridge.sdk.config.QwenBridgeClientConfig;
import io.qwenbridge.sdk.exception.QwenBridgeApiException;
import io.qwenbridge.sdk.exception.QwenBridgeTransportException;
import io.qwenbridge.sdk.retry.RetryPolicy;
import io.qwenbridge.sdk.search.SearchAnalyzeRequest;
import io.qwenbridge.sdk.search.SearchAnalyzeResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class QwenBridgeClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldMapSuccessfulAnalyzeResponse() throws Exception {
        server = startServer(200, """
                {
                  "requestId": "req-123",
                  "processingTimeMs": 42,
                  "originalQuery": "iphone",
                  "language": "en",
                  "intent": "PRODUCT_SEARCH",
                  "decision": "SEARCH",
                  "confidence": 0.91,
                  "rewrites": ["iphone"],
                  "threatReasons": [],
                  "semanticValidated": true,
                  "semanticScore": 0.88,
                  "policyPassed": true,
                  "policyViolations": [],
                  "executionPlan": {},
                  "executionResult": {},
                  "search": {},
                  "cache": {},
                  "pipelineTrace": []
                }
                """, null);

        SearchAnalyzeResponse response = client().analyze(
                SearchAnalyzeRequest.withRequestId("req-123", "iphone")
        );

        assertEquals("req-123", response.requestId());
        assertEquals("iphone", response.originalQuery());
        assertEquals("PRODUCT_SEARCH", response.intent());
        assertEquals("SEARCH", response.decision());
        assertEquals(0.91, response.confidence());
        assertTrue(response.policyPassed());
    }

    @Test
    void shouldPropagateRequestIdHeader() throws Exception {
        AtomicReference<String> requestIdHeader = new AtomicReference<>();

        server = startServer(200, """
                {
                  "requestId": "req-456",
                  "processingTimeMs": 1,
                  "originalQuery": "table",
                  "language": "en",
                  "intent": "PRODUCT_SEARCH",
                  "decision": "SEARCH",
                  "confidence": 1.0,
                  "rewrites": [],
                  "threatReasons": [],
                  "semanticValidated": true,
                  "semanticScore": 1.0,
                  "policyPassed": true,
                  "policyViolations": [],
                  "executionPlan": {},
                  "executionResult": {},
                  "search": {},
                  "cache": {},
                  "pipelineTrace": []
                }
                """, requestIdHeader);

        client().analyze(SearchAnalyzeRequest.withRequestId("req-456", "table"));

        assertEquals("req-456", requestIdHeader.get());
    }

    @Test
    void shouldMapApiErrorResponse() throws Exception {
        server = startServer(400, """
                {
                  "timestamp": "2026-07-04T13:00:00Z",
                  "status": 400,
                  "error": "Bad Request",
                  "code": "VALIDATION_ERROR",
                  "message": "query must not be blank",
                  "path": "/api/v1/search/analyze",
                  "requestId": "req-error"
                }
                """, null);

        QwenBridgeApiException exception = assertThrows(
                QwenBridgeApiException.class,
                () -> client().analyze(SearchAnalyzeRequest.withRequestId("req-error", "iphone"))
        );

        assertEquals(400, exception.apiError().status());
        assertEquals("VALIDATION_ERROR", exception.apiError().code());
        assertEquals("req-error", exception.apiError().requestId());
    }


    @Test
    void shouldMapSuccessfulAsyncAnalyzeResponse() throws Exception {
        server = startServer(200, """
                {
                  "requestId": "async-req-123",
                  "processingTimeMs": 7,
                  "originalQuery": "laptop",
                  "language": "en",
                  "intent": "PRODUCT_SEARCH",
                  "decision": "SEARCH",
                  "confidence": 0.97,
                  "rewrites": ["laptop"],
                  "threatReasons": [],
                  "semanticValidated": true,
                  "semanticScore": 0.94,
                  "policyPassed": true,
                  "policyViolations": [],
                  "executionPlan": {},
                  "executionResult": {},
                  "search": {},
                  "cache": {},
                  "pipelineTrace": []
                }
                """, null);

        SearchAnalyzeResponse response = client()
                .analyzeAsync(SearchAnalyzeRequest.withRequestId("async-req-123", "laptop"))
                .join();

        assertEquals("async-req-123", response.requestId());
        assertEquals("laptop", response.originalQuery());
        assertEquals("PRODUCT_SEARCH", response.intent());
        assertEquals("SEARCH", response.decision());
        assertEquals(0.97, response.confidence());
    }



    @Test
    void shouldRetrySyncAnalyzeOnServiceUnavailableAndReturnSuccess() throws Exception {
        AtomicInteger calls = new AtomicInteger();

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/search/analyze", exchange -> {
            int call = calls.incrementAndGet();

            if (call == 1) {
                String errorBody = """
                        {
                          "timestamp": "2026-07-04T14:00:00Z",
                          "status": 503,
                          "error": "Service Unavailable",
                          "code": "AI_PROVIDER_ERROR",
                          "message": "provider temporarily unavailable",
                          "path": "/api/v1/search/analyze",
                          "requestId": "retry-req"
                        }
                        """;

                byte[] bytes = errorBody.getBytes();
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(503, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
                return;
            }

            String successBody = """
                    {
                      "requestId": "retry-req",
                      "processingTimeMs": 9,
                      "originalQuery": "monitor",
                      "language": "en",
                      "intent": "PRODUCT_SEARCH",
                      "decision": "SEARCH",
                      "confidence": 0.99,
                      "rewrites": ["monitor"],
                      "threatReasons": [],
                      "semanticValidated": true,
                      "semanticScore": 0.98,
                      "policyPassed": true,
                      "policyViolations": [],
                      "executionPlan": {},
                      "executionResult": {},
                      "search": {},
                      "cache": {},
                      "pipelineTrace": []
                    }
                    """;

            byte[] bytes = successBody.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        QwenBridgeClient retryingClient = new QwenBridgeClient(new QwenBridgeClientConfig(
                URI.create("http://localhost:" + server.getAddress().getPort()),
                Duration.ofSeconds(1),
                Duration.ofSeconds(5),
                new RetryPolicy(
                        2,
                        Duration.ofMillis(1),
                        Duration.ofMillis(1)
                )
        ));

        SearchAnalyzeResponse response = retryingClient.analyze(
                SearchAnalyzeRequest.withRequestId("retry-req", "monitor")
        );

        assertEquals(2, calls.get());
        assertEquals("retry-req", response.requestId());
        assertEquals("monitor", response.originalQuery());
        assertEquals(0.99, response.confidence());
    }

    @Test
    void shouldWrapSynchronousTransportFailure() {
        QwenBridgeClient client = new QwenBridgeClient(new QwenBridgeClientConfig(
                URI.create("http://localhost:" + unusedPort()),
                Duration.ofMillis(100),
                Duration.ofMillis(300)
        ));

        QwenBridgeTransportException exception = assertThrows(
                QwenBridgeTransportException.class,
                () -> client.analyze(SearchAnalyzeRequest.of("best coffee in Stockholm"))
        );

        assertEquals("Failed to call QwenBridge API", exception.getMessage());
        assertNotNull(exception.getCause());
    }

    @Test
    void shouldWrapAsynchronousTransportFailure() {
        QwenBridgeClient client = new QwenBridgeClient(new QwenBridgeClientConfig(
                URI.create("http://localhost:" + unusedPort()),
                Duration.ofMillis(100),
                Duration.ofMillis(300)
        ));

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> client.analyzeAsync(SearchAnalyzeRequest.of("best coffee in Stockholm")).join()
        );

        assertInstanceOf(QwenBridgeTransportException.class, exception.getCause());

        QwenBridgeTransportException transportException =
                (QwenBridgeTransportException) exception.getCause();

        assertEquals("Failed to call QwenBridge API", transportException.getMessage());
        assertNotNull(transportException.getCause());
    }

    @Test
    void shouldRejectBlankQueryBeforeHttpCall() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SearchAnalyzeRequest.of(" ")
        );

        assertEquals("query must not be blank", exception.getMessage());
    }

    private int unusedPort() {
        try {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);
            int port = httpServer.getAddress().getPort();
            httpServer.stop(0);
            return port;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to allocate unused port", e);
        }
    }

    private QwenBridgeClient client() {
        return new QwenBridgeClient(new QwenBridgeClientConfig(
                URI.create("http://localhost:" + server.getAddress().getPort()),
                Duration.ofSeconds(1),
                Duration.ofSeconds(5)
        ));
    }

    private HttpServer startServer(
            int status,
            String responseBody,
            AtomicReference<String> requestIdHeader
    ) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/api/v1/search/analyze", exchange -> {
            if (requestIdHeader != null) {
                requestIdHeader.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
            }

            byte[] bytes = responseBody.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        httpServer.start();
        return httpServer;
    }
}

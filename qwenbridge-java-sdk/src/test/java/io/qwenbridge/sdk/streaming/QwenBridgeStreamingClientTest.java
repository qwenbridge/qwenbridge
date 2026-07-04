package io.qwenbridge.sdk.streaming;

import com.sun.net.httpserver.HttpServer;
import io.qwenbridge.sdk.config.QwenBridgeClientConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QwenBridgeStreamingClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldConsumeConnectedSseEvent() throws Exception {
        server = startSseServer("""
                event: stream.connected
                data: {"requestId":"req-stream","sessionId":"session-1"}

                """, 200);

        QwenBridgeStreamingClient client = client();

        List<StreamingEvent> events = new ArrayList<>();

        client.stream("req-stream", events::add).join();

        assertEquals(1, events.size());
        assertEquals("stream.connected", events.getFirst().event());
        assertEquals(
                "{\"requestId\":\"req-stream\",\"sessionId\":\"session-1\"}",
                events.getFirst().data()
        );
    }

    @Test
    void shouldRejectBlankRequestId() {
        QwenBridgeStreamingClient client = new QwenBridgeStreamingClient(
                QwenBridgeClientConfig.localDefault()
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> client.stream(" ", event -> {})
        );

        assertEquals("requestId must not be blank", exception.getMessage());
    }

    @Test
    void shouldFailWhenStreamReturnsNonSuccessStatus() throws Exception {
        server = startSseServer("error", 500);

        QwenBridgeStreamingClient client = client();

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> client.stream("req-stream", event -> {}).join()
        );

        assertNotNull(exception.getCause());
    }

    private QwenBridgeStreamingClient client() {
        return new QwenBridgeStreamingClient(new QwenBridgeClientConfig(
                URI.create("http://localhost:" + server.getAddress().getPort()),
                Duration.ofSeconds(1),
                Duration.ofSeconds(5)
        ));
    }

    private HttpServer startSseServer(
            String body,
            int status
    ) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/api/v1/search/stream/req-stream", exchange -> {
            byte[] bytes = body.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        httpServer.start();
        return httpServer;
    }
}

package io.qwenbridge.sdk.streaming;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import io.qwenbridge.sdk.config.QwenBridgeClientConfig;
import io.qwenbridge.sdk.streaming.payload.AICompletedStreamingPayload;
import io.qwenbridge.sdk.streaming.payload.AIFailedStreamingPayload;
import io.qwenbridge.sdk.streaming.payload.AITokenStreamingPayload;
import io.qwenbridge.sdk.streaming.payload.ConnectedStreamingPayload;
import io.qwenbridge.sdk.streaming.payload.UnknownStreamingPayload;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TypedStreamingPayloadTest {

  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void shouldMapKnownStreamingPayloads() throws Exception {
    server =
        startSseServer(
            """
event: stream.connected
data: {"requestId":"req-typed","sessionId":"session-1"}

event: ai.token
data: {"requestId":"req-typed","tokenIndex":0,"content":"hello","terminal":false}

event: ai.completed
data: {"requestId":"req-typed","tokenCount":1,"terminal":true}

event: ai.failed
data: {"requestId":"req-typed","code":"AI_PROVIDER_ERROR","message":"provider failed","terminal":true}

""");

    List<TypedStreamingEvent> events = new ArrayList<>();

    client().streamTyped("req-typed", events::add).join();

    assertEquals(4, events.size());

    assertEquals("stream.connected", events.get(0).eventName());
    assertInstanceOf(ConnectedStreamingPayload.class, events.get(0).payload());

    ConnectedStreamingPayload connected = (ConnectedStreamingPayload) events.get(0).payload();

    assertEquals("req-typed", connected.requestId());
    assertEquals("session-1", connected.sessionId());

    assertEquals("ai.token", events.get(1).eventName());
    assertInstanceOf(AITokenStreamingPayload.class, events.get(1).payload());

    AITokenStreamingPayload token = (AITokenStreamingPayload) events.get(1).payload();

    assertEquals("req-typed", token.requestId());
    assertEquals(0, token.tokenIndex());
    assertEquals("hello", token.content());
    assertFalse(token.terminal());

    assertEquals("ai.completed", events.get(2).eventName());
    assertInstanceOf(AICompletedStreamingPayload.class, events.get(2).payload());

    AICompletedStreamingPayload completed = (AICompletedStreamingPayload) events.get(2).payload();

    assertEquals("req-typed", completed.requestId());
    assertEquals(1, completed.tokenCount());
    assertTrue(completed.terminal());

    assertEquals("ai.failed", events.get(3).eventName());
    assertInstanceOf(AIFailedStreamingPayload.class, events.get(3).payload());

    AIFailedStreamingPayload failed = (AIFailedStreamingPayload) events.get(3).payload();

    assertEquals("req-typed", failed.requestId());
    assertEquals("AI_PROVIDER_ERROR", failed.code());
    assertEquals("provider failed", failed.message());
    assertTrue(failed.terminal());
  }

  @Test
  void shouldKeepUnknownPayloadAsRawData() throws Exception {
    server =
        startSseServer(
            """
            event: custom.event
            data: {"hello":"world"}

            """);

    List<TypedStreamingEvent> events = new ArrayList<>();

    client().streamTyped("req-typed", events::add).join();

    assertEquals(1, events.size());
    assertEquals("custom.event", events.getFirst().eventName());
    assertInstanceOf(UnknownStreamingPayload.class, events.getFirst().payload());

    UnknownStreamingPayload unknown = (UnknownStreamingPayload) events.getFirst().payload();

    assertEquals("{\"hello\":\"world\"}", unknown.rawData());
  }

  @Test
  void shouldKeepInvalidKnownPayloadAsUnknownRawData() throws Exception {
    server =
        startSseServer(
            """
            event: ai.token
            data: {not-valid-json}

            """);

    List<TypedStreamingEvent> events = new ArrayList<>();

    client().streamTyped("req-typed", events::add).join();

    assertEquals(1, events.size());
    assertEquals("ai.token", events.getFirst().eventName());
    assertInstanceOf(UnknownStreamingPayload.class, events.getFirst().payload());

    UnknownStreamingPayload unknown = (UnknownStreamingPayload) events.getFirst().payload();

    assertEquals("{not-valid-json}", unknown.rawData());
  }

  private QwenBridgeStreamingClient client() {
    return new QwenBridgeStreamingClient(
        new QwenBridgeClientConfig(
            URI.create("http://localhost:" + server.getAddress().getPort()),
            Duration.ofSeconds(1),
            Duration.ofSeconds(5)));
  }

  private HttpServer startSseServer(String body) throws IOException {
    HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);
    httpServer.createContext(
        "/api/v1/search/stream/req-typed",
        exchange -> {
          byte[] bytes = body.getBytes();
          exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    httpServer.start();
    return httpServer;
  }
}

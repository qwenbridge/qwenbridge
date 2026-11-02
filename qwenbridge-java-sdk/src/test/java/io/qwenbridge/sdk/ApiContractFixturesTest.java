package io.qwenbridge.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpServer;
import io.qwenbridge.sdk.commerce.CommerceClient;
import io.qwenbridge.sdk.commerce.CommerceContracts;
import io.qwenbridge.sdk.exception.QwenBridgeApiError;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * SDK half of the 1.0 API contract fixtures (QB-185). The server's {@code CommerceApiTest} fails
 * when the server emits a field these fixtures lack, so a server change cannot silently break this
 * SDK: the v1 error body is parsed strictly (as released SDKs do), and v2 streams with the 1.0
 * fields, heartbeat comments and a typed failure are consumed to their terminal event.
 */
class ApiContractFixturesTest {

  static byte[] fixture(String name) throws Exception {
    try (InputStream in = ApiContractFixturesTest.class.getResourceAsStream("/contract/" + name)) {
      return in.readAllBytes();
    }
  }

  /** The error type exactly as released in SDK 0.9.1: seven fields, unknown fields rejected. */
  record ReleasedApiError(
      java.time.Instant timestamp,
      int status,
      String error,
      String code,
      String message,
      String path,
      String requestId) {}

  @Test
  void theV1ErrorBodyStillParsesWithTheReleasedStrictType() throws Exception {
    var strict = new ObjectMapper().registerModule(new JavaTimeModule());
    var released = strict.readValue(fixture("v1-api-error.json"), ReleasedApiError.class);
    assertEquals("TENANT_API_REQUIRED", released.code());
    // The current type reads the same body, and v2 bodies with category and retryable.
    var current = strict.readValue(fixture("v1-api-error.json"), QwenBridgeApiError.class);
    assertEquals(409, current.status());
    assertEquals(null, current.category());
  }

  @Test
  void v2StreamsWithTheOneZeroFieldsAndHeartbeatsAreConsumedToTheirTerminalEvent()
      throws Exception {
    for (String name : List.of("v2-search-stream-completed.sse", "v2-search-stream-failed.sse")) {
      byte[] body = fixture(name);
      var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/",
          exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
          });
      server.start();
      try (var client =
          new CommerceClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "")) {
        var events = new ArrayList<CommerceContracts.Event>();
        client.stream(new CommerceContracts.Request("shoes"), events::add);
        assertEquals(2, events.size(), name);
        assertEquals("search.started", events.getFirst().kind());
        assertTrue(events.getLast().kind().startsWith("search."), name);
      } finally {
        server.stop(0);
      }
    }
  }
}

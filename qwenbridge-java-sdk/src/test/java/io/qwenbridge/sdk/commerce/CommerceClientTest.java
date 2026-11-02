package io.qwenbridge.sdk.commerce;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class CommerceClientTest {
  @Test
  void typedSessionAndConsentContractsUseRevisionAndOwnedPaths() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var requests = new ArrayList<String>();
    server.createContext(
        "/",
        exchange -> {
          var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          requests.add(
              exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " " + body);
          String result =
              exchange.getRequestURI().getPath().endsWith("/relaxations")
                  ? "[{\"id\":\"offer\",\"description\":\"Raise budget\",\"status\":\"OFFERED\"}]"
                  : "{\"id\":\"session\",\"tenant\":\"shop\",\"subject\":\"alice\",\"backend\":\"opensearch\",\"revision\":2,\"last\":{\"apiVersion\":2,\"status\":\"OK\",\"nextCursor\":\"cursor\",\"timings\":{\"execution\":5},\"policies\":[],\"lockedFields\":[\"price\"]}}";
          var bytes = result.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    try (var client =
        new CommerceClient(
            URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "test-only")) {
      assertEquals(
          "session",
          client
              .createSession(
                  new CommerceContracts.SessionCreate(1800, null, java.util.List.of("price")))
              .id());
      assertEquals(2, client.session("session").revision());
      assertEquals(
          "cursor",
          client
              .turn(
                  "session",
                  new CommerceContracts.SessionTurn("size 42", 1, false, "KEYWORD", false))
              .last()
              .nextCursor());
      assertEquals("offer", client.proposals("session").getFirst().id());
      assertEquals(5L, client.accept("session", "offer", 1).last().timings().get("execution"));
      assertTrue(
          requests
              .getLast()
              .contains("/sessions/session/relaxations/offer/accept {\"expectedRevision\":1}"));
      assertThrows(IllegalArgumentException.class, () -> client.session("../another"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void typedRestAsyncAndSseUseTheSameVersionedContractAndCredential() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          if (!"Bearer test-only".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
            return;
          }
          String body =
              exchange.getRequestURI().getPath().endsWith("/stream")
                  ? "event:search.started\n"
                        + "data:{\"version\":2,\"kind\":\"search.started\",\"requestId\":\"r\"}\n\n"
                        + "event:search.completed\n"
                        + "data:{\"version\":2,\"kind\":\"search.completed\",\"requestId\":\"r\",\"response\":{\"apiVersion\":2,\"status\":\"OK\"}}\n\n"
                  : "{\"apiVersion\":2,\"requestId\":\"r\",\"status\":\"CLARIFICATION_REQUIRED\",\"clarificationQuestions\":[\"Which"
                        + " size?\"],\"futureField\":true}";
          var bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    try (var client =
        new CommerceClient(
            URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "test-only")) {
      var request = new CommerceContracts.Request("کفش مشکی");
      assertEquals(
          java.util.List.of("Which size?"), client.search(request).clarificationQuestions());
      assertEquals(2, client.searchAsync(request).get().apiVersion());
      var events = new ArrayList<CommerceContracts.Event>();
      client.stream(request, events::add);
      assertEquals(
          java.util.List.of("search.started", "search.completed"),
          events.stream().map(CommerceContracts.Event::kind).toList());
      assertEquals("OK", events.getLast().response().status());
    } finally {
      server.stop(0);
    }
  }
}

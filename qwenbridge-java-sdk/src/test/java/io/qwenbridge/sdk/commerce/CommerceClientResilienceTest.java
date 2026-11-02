package io.qwenbridge.sdk.commerce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.qwenbridge.sdk.exception.QwenBridgeApiException;
import io.qwenbridge.sdk.exception.QwenBridgeIncompatibleServerException;
import io.qwenbridge.sdk.exception.QwenBridgeTransportException;
import io.qwenbridge.sdk.retry.RetryPolicy;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** QB-186, QB-188, QB-190: typed errors, retries, timeouts, cancellation and stream rules. */
class CommerceClientResilienceTest {

  record Seen(String method, String path, String requestTimeout, String body, String accept) {}

  record Reply(int status, String body, long delayMs) {
    static Reply json(int status, String body) {
      return new Reply(status, body, 0);
    }
  }

  static final String OK =
      "{\"apiVersion\":2,\"requestId\":\"r\",\"status\":\"OK\",\"results\":{\"hits\":[],\"totalHits\":0,\"tookMillis\":1}}";
  static final String UNAVAILABLE =
      "{\"status\":503,\"code\":\"DEPENDENCY_UNAVAILABLE\",\"category\":\"DEPENDENCY\",\"retryable\":true,\"message\":\"A"
          + " dependency is unavailable\",\"requestId\":\"r\"}";

  final List<Seen> seen = new CopyOnWriteArrayList<>();
  HttpServer server;

  CommerceClient client(Function<Seen, Reply> handler, Duration timeout, int attempts)
      throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          var request =
              new Seen(
                  exchange.getRequestMethod(),
                  exchange.getRequestURI().getPath(),
                  exchange.getRequestHeaders().getFirst("X-Request-Timeout-Ms"),
                  new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                  exchange.getRequestHeaders().getFirst("Accept"));
          seen.add(request);
          reply(exchange, handler.apply(request));
        });
    server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
    server.start();
    return new CommerceClient(
        new CommerceClientConfig(
            URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
            "test-only-secret",
            Duration.ofSeconds(2),
            timeout,
            new RetryPolicy(attempts, Duration.ofMillis(5), Duration.ofMillis(20))));
  }

  static void reply(HttpExchange exchange, Reply reply) throws IOException {
    try {
      if (reply.delayMs() > 0) Thread.sleep(reply.delayMs());
      byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(reply.status(), bytes.length == 0 ? -1 : bytes.length);
      if (bytes.length > 0) exchange.getResponseBody().write(bytes);
    } catch (InterruptedException | IOException ignored) {
      // client went away
    } finally {
      exchange.close();
    }
  }

  @AfterEach
  void stop() {
    if (server != null) server.stop(0);
  }

  @Test
  void errorsAreTypedWithCodeCategoryAndRetryability() throws Exception {
    var client =
        client(
            r ->
                r.path().endsWith("/turns")
                    ? Reply.json(502, "<html>bad gateway</html>")
                    : Reply.json(
                        400,
                        "{\"status\":400,\"code\":\"BAD_REQUEST\",\"category\":\"VALIDATION\",\"retryable\":false,\"message\":\"Page"
                            + " size must be at most 100\",\"futureField\":1}"),
            Duration.ofSeconds(5),
            3);
    var error =
        assertThrows(
            QwenBridgeApiException.class, () -> client.search(new CommerceContracts.Request("x")));
    assertEquals(400, error.status());
    assertEquals("BAD_REQUEST", error.code());
    assertEquals("VALIDATION", error.category());
    assertEquals(Boolean.FALSE, error.apiError().retryable());
    assertEquals(1, seen.size(), "validation errors are not retried");
    var opaque =
        assertThrows(
            QwenBridgeApiException.class,
            () -> client.turn("s1", new CommerceContracts.SessionTurn("x", 0, false, null, false)));
    assertEquals(502, opaque.status(), "a non-JSON error body still yields a typed status");
    assertFalse(opaque.getMessage().contains("test-only-secret"));
  }

  @Test
  void onlySafeOperationsAreRetriedAndAlwaysWithTheSameRequestId() throws Exception {
    var client = client(r -> Reply.json(503, UNAVAILABLE), Duration.ofSeconds(5), 3);
    assertThrows(
        QwenBridgeApiException.class, () -> client.search(new CommerceContracts.Request("shoes")));
    assertEquals(3, seen.size(), "a retryable failure is attempted maxAttempts times");
    var ids =
        seen.stream()
            .map(s -> s.body().replaceAll(".*\"requestId\":\"([^\"]+)\".*", "$1"))
            .distinct()
            .toList();
    assertEquals(1, ids.size(), "retries reuse one request ID: " + ids);
    seen.clear();
    assertThrows(
        QwenBridgeApiException.class,
        () -> client.turn("s1", new CommerceContracts.SessionTurn("x", 0, false, null, false)));
    assertThrows(QwenBridgeApiException.class, () -> client.accept("s1", "p1", 1));
    assertThrows(
        QwenBridgeApiException.class,
        () -> client.createSession(new CommerceContracts.SessionCreate(null, null, null)));
    assertEquals(3, seen.size(), "state-changing calls are attempted exactly once each");
  }

  @Test
  void transientFailuresRecoverAndTheTimeoutIsSentAndEnforced() throws Exception {
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    var client =
        client(
            r ->
                r.path().equals("/api/v2/search")
                    ? (calls.incrementAndGet() == 1
                        ? Reply.json(503, UNAVAILABLE)
                        : Reply.json(200, OK))
                    : new Reply(200, OK, 3_000),
            Duration.ofMillis(800),
            2);
    assertEquals("OK", client.search(new CommerceContracts.Request("shoes")).status());
    assertEquals("800", seen.getFirst().requestTimeout(), "the server gets the same deadline");
    long started = System.nanoTime();
    assertThrows(QwenBridgeTransportException.class, () -> client.session("slow"));
    assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 2_900);
  }

  @Test
  void cancellationReachesTheServer() throws Exception {
    var deleted = new CountDownLatch(1);
    var client =
        client(
            r -> {
              if (r.method().equals("DELETE")) {
                deleted.countDown();
                return r.path().endsWith("/known")
                    ? Reply.json(200, "{\"requestId\":\"known\",\"cancelled\":true}")
                    : Reply.json(404, "{\"status\":404,\"code\":\"NOT_FOUND\"}");
              }
              return new Reply(200, OK, 5_000);
            },
            Duration.ofSeconds(10),
            1);
    assertTrue(client.cancel("known"));
    assertFalse(client.cancel("unknown"));
    assertThrows(IllegalArgumentException.class, () -> client.cancel("../x"));
    seen.clear();
    var pending =
        client.searchAsync(new CommerceContracts.Request("shoes").withRequestId("async-1"));
    Thread.sleep(200);
    pending.cancel(true);
    assertTrue(pending.isCancelled());
    // The server is told, so it stops its own work.
    for (int i = 0; i < 50 && seen.stream().noneMatch(s -> s.method().equals("DELETE")); i++)
      Thread.sleep(50);
    assertTrue(
        seen.stream()
            .anyMatch(
                s -> s.method().equals("DELETE") && s.path().equals("/api/v2/requests/async-1")));
  }

  static String sse(String... events) {
    var out = new StringBuilder();
    for (String e : events) out.append(e).append("\n\n");
    return out.toString();
  }

  @Test
  void streamsFollowTheContractAndRejectIncompatibleOrDisorderedEvents() throws Exception {
    String started =
        "event:search.started\n"
            + "data:{\"version\":2,\"kind\":\"search.started\",\"requestId\":\"r\",\"sequence\":1,\"terminal\":false}";
    String failed =
        "event:search.failed\n"
            + "data:{\"version\":2,\"kind\":\"search.failed\",\"requestId\":\"r\",\"sequence\":2,\"terminal\":true,\"message\":\"Search"
            + " could not"
            + " complete\",\"error\":{\"code\":\"TIMEOUT\",\"category\":\"TIMEOUT\",\"message\":\"Request"
            + " deadline exceeded\",\"retryable\":true}}";
    var bodies =
        new java.util.ArrayDeque<>(
            List.of(
                sse(
                    started,
                    ":heartbeat",
                    failed,
                    "event:search.completed\ndata:{\"version\":2,\"kind\":\"search.completed\"}"),
                sse(started, started.replace("search.started\"", "search.started\"")),
                sse(started.replace("\"search.started\",", "\"search.progress\",")),
                sse(started.replace("\"version\":2", "\"version\":3")),
                sse(started)));
    var client = client(r -> Reply.json(200, bodies.poll()), Duration.ofSeconds(5), 1);
    var events = new ArrayList<CommerceContracts.Event>();
    client.stream(new CommerceContracts.Request("shoes"), events::add);
    assertEquals(2, events.size(), "nothing after the terminal event is read");
    assertEquals("TIMEOUT", events.getLast().error().code());
    assertEquals("text/event-stream", seen.getFirst().accept(), "the server refuses other types");
    assertTrue(events.getLast().isTerminal());
    assertThrows(
        QwenBridgeIncompatibleServerException.class,
        () -> client.stream(new CommerceContracts.Request("shoes"), e -> {}),
        "a repeated sequence number");
    assertThrows(
        QwenBridgeIncompatibleServerException.class,
        () -> client.stream(new CommerceContracts.Request("shoes"), e -> {}),
        "an unknown event kind");
    assertThrows(
        QwenBridgeIncompatibleServerException.class,
        () -> client.stream(new CommerceContracts.Request("shoes"), e -> {}),
        "an unknown event version");
    assertThrows(
        QwenBridgeTransportException.class,
        () -> client.stream(new CommerceContracts.Request("shoes"), e -> {}),
        "a stream without a terminal event");
  }

  @Test
  void incompatibleServersAreRefusedInsteadOfSilentlyMapped() throws Exception {
    var client =
        client(
            r ->
                r.path().equals("/api/v2/version")
                    ? Reply.json(
                        200,
                        "{\"version\":\"2.0.0\",\"apiVersions\":[\"v2\"],\"contracts\":{\"sse\":\"progress-event/3\",\"error\":\"api-error/2\"}}")
                    : Reply.json(200, OK.replace("\"apiVersion\":2", "\"apiVersion\":3")),
            Duration.ofSeconds(5),
            1);
    assertThrows(
        QwenBridgeIncompatibleServerException.class,
        () -> client.search(new CommerceContracts.Request("shoes")));
    assertThrows(QwenBridgeIncompatibleServerException.class, client::requireCompatibleServer);
    assertFalse(
        CommerceClientConfig.of(URI.create("http://x"), "test-only-secret")
            .toString()
            .contains("test-only-secret"));
  }
}

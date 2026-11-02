package io.qwenbridge.sdk.commerce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.qwenbridge.sdk.exception.QwenBridgeApiError;
import io.qwenbridge.sdk.exception.QwenBridgeApiException;
import io.qwenbridge.sdk.exception.QwenBridgeIncompatibleServerException;
import io.qwenbridge.sdk.exception.QwenBridgeTransportException;
import io.qwenbridge.sdk.retry.ExponentialBackoff;
import io.qwenbridge.sdk.retry.RetryClassifier;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Typed client for the tenant-scoped v2 API (QB-186, QB-188, QB-190).
 *
 * <ul>
 *   <li>Failures are typed: {@link QwenBridgeApiException} (with the server's code, category and
 *       retryability), {@link QwenBridgeTransportException}, or {@link
 *       QwenBridgeIncompatibleServerException} for a response of an unsupported contract version.
 *   <li>Every call has a timeout, sent to the server as {@code X-Request-Timeout-Ms} so that its
 *       work stops too.
 *   <li>Only safe operations are retried (search, refine and reads), with exponential backoff and
 *       the same request ID on every attempt. Session turns, acceptances, streams and cancellations
 *       are never retried.
 *   <li>Cancelling a future from {@link #searchAsync} or {@link #streamAsync} also asks the server
 *       to cancel the request ({@link #cancel}).
 * </ul>
 *
 * Bearer credentials stay in headers and never appear in exceptions or {@code toString}.
 */
public final class CommerceClient implements AutoCloseable {

  static final int API_VERSION = 2;
  static final String SSE_CONTRACT = "progress-event/2";
  static final String ERROR_CONTRACT = "api-error/2";
  static final int MAX_EVENT_BYTES = 4_194_304;
  private static final Set<String> KINDS =
      Set.of(
          "search.started",
          "search.completed",
          "search.failed",
          "session.started",
          "session.completed",
          "session.failed");

  private final CommerceClientConfig config;
  private final HttpClient client;
  private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

  public CommerceClient(URI base, String bearerToken) {
    this(CommerceClientConfig.of(base, bearerToken));
  }

  public CommerceClient(CommerceClientConfig config) {
    this.config = java.util.Objects.requireNonNull(config, "config must not be null");
    this.client = HttpClient.newBuilder().connectTimeout(config.connectTimeout()).build();
  }

  // ---- search ----

  public CommerceContracts.Response search(CommerceContracts.Request request) {
    var identified = identified(request);
    return retrying(() -> response(send("POST", "/api/v2/search", identified)));
  }

  public CommerceContracts.Response refine(String previous, CommerceContracts.Request request) {
    if (previous == null || !previous.matches("[A-Za-z0-9._-]{1,128}"))
      throw new IllegalArgumentException("Invalid previous request identifier");
    var identified = identified(request);
    return retrying(
        () -> response(send("POST", "/api/v2/search/" + previous + "/refine", identified)));
  }

  /**
   * Searches without blocking. Cancelling the returned future aborts the HTTP exchange and asks the
   * server to cancel the request.
   */
  public CompletableFuture<CommerceContracts.Response> searchAsync(
      CommerceContracts.Request request) {
    var identified = identified(request);
    var future =
        client
            .sendAsync(
                request("POST", "/api/v2/search", identified), HttpResponse.BodyHandlers.ofString())
            .thenApply(r -> response(checked(r.statusCode(), r.body(), "/api/v2/search")));
    var result = new CompletableFuture<CommerceContracts.Response>();
    future.whenComplete(
        (value, error) -> {
          if (error != null) result.completeExceptionally(unwrap(error));
          else result.complete(value);
        });
    result.whenComplete(
        (value, error) -> {
          if (result.isCancelled()) {
            future.cancel(true);
            cancelQuietly(identified.requestId());
          }
        });
    return result;
  }

  // ---- streams ----

  public void stream(CommerceContracts.Request request, Consumer<CommerceContracts.Event> handler) {
    stream(
        "/api/v2/search/stream",
        identified(request),
        CommerceContracts.Event.class,
        handler,
        new AtomicReference<>());
  }

  /**
   * Streams on a virtual thread. Cancelling the returned future closes the connection (the server
   * then stops the request) and asks the server to cancel it.
   */
  public CompletableFuture<Void> streamAsync(
      CommerceContracts.Request request, Consumer<CommerceContracts.Event> handler) {
    var identified = identified(request);
    var lines = new AtomicReference<Stream<String>>();
    var result = new CompletableFuture<Void>();
    Thread.ofVirtual()
        .name("qwenbridge-stream-" + identified.requestId())
        .start(
            () -> {
              try {
                stream(
                    "/api/v2/search/stream",
                    identified,
                    CommerceContracts.Event.class,
                    handler,
                    lines);
                result.complete(null);
              } catch (RuntimeException e) {
                result.completeExceptionally(e);
              }
            });
    result.whenComplete(
        (value, error) -> {
          if (result.isCancelled()) {
            var open = lines.get();
            if (open != null) open.close();
            cancelQuietly(identified.requestId());
          }
        });
    return result;
  }

  public void streamTurn(
      String id,
      CommerceContracts.SessionTurn request,
      Consumer<CommerceContracts.SessionEvent> handler) {
    stream(
        "/api/v2/sessions/" + id(id) + "/turns/stream",
        request,
        CommerceContracts.SessionEvent.class,
        handler,
        new AtomicReference<>());
  }

  // ---- sessions (state-changing calls are never retried) ----

  public CommerceContracts.Session createSession(CommerceContracts.SessionCreate input) {
    return json(send("POST", "/api/v2/sessions", input), CommerceContracts.Session.class);
  }

  public CommerceContracts.Session session(String id) {
    return retrying(
        () ->
            json(send("GET", "/api/v2/sessions/" + id(id), null), CommerceContracts.Session.class));
  }

  public CommerceContracts.Session turn(String id, CommerceContracts.SessionTurn input) {
    return json(
        send("POST", "/api/v2/sessions/" + id(id) + "/turns", input),
        CommerceContracts.Session.class);
  }

  public List<CommerceContracts.Proposal> proposals(String id) {
    return List.of(
        retrying(
            () ->
                json(
                    send("GET", "/api/v2/sessions/" + id(id) + "/relaxations", null),
                    CommerceContracts.Proposal[].class)));
  }

  public CommerceContracts.Session accept(String session, String proposal, int revision) {
    return json(
        send(
            "POST",
            "/api/v2/sessions/" + id(session) + "/relaxations/" + id(proposal) + "/accept",
            Map.of("expectedRevision", revision)),
        CommerceContracts.Session.class);
  }

  // ---- control and metadata ----

  /**
   * Asks the server to cancel the caller's own in-flight request. Returns false when the server
   * knows no such request (already finished, never started, or not the caller's).
   */
  public boolean cancel(String requestId) {
    try {
      send("DELETE", "/api/v2/requests/" + id(requestId), null);
      return true;
    } catch (QwenBridgeApiException e) {
      if (e.status() == 404) return false;
      throw e;
    }
  }

  /** The server's API and contract versions and limits. */
  public CommerceContracts.ServerVersion version() {
    return retrying(
        () -> json(send("GET", "/api/v2/version", null), CommerceContracts.ServerVersion.class));
  }

  /**
   * Fails with {@link QwenBridgeIncompatibleServerException} unless the server implements the v2
   * contracts this SDK speaks.
   */
  public CommerceContracts.ServerVersion requireCompatibleServer() {
    var version = version();
    if (!version.apiVersions().contains("v2")
        || !SSE_CONTRACT.equals(version.contracts().get("sse"))
        || !ERROR_CONTRACT.equals(version.contracts().get("error")))
      throw new QwenBridgeIncompatibleServerException(
          "Server "
              + version.version()
              + " does not implement v2 with "
              + SSE_CONTRACT
              + " and "
              + ERROR_CONTRACT);
    return version;
  }

  // ---- internals ----

  /** A request ID is assigned up front, so retries and cancellation refer to the same request. */
  private static CommerceContracts.Request identified(CommerceContracts.Request request) {
    java.util.Objects.requireNonNull(request, "request must not be null");
    return request.requestId() == null
        ? request.withRequestId(UUID.randomUUID().toString())
        : request;
  }

  private String id(String value) {
    if (value == null || !value.matches("[A-Za-z0-9._-]{1,128}"))
      throw new IllegalArgumentException("Invalid resource identifier");
    return value;
  }

  @FunctionalInterface
  private interface Call<T> {
    T run();
  }

  /** Bounded retries with exponential backoff, for safe operations only. */
  private <T> T retrying(Call<T> call) {
    var policy = config.retryPolicy();
    for (int attempt = 1; ; attempt++) {
      try {
        return call.run();
      } catch (RuntimeException e) {
        if (attempt >= policy.maxAttempts() || !RetryClassifier.isRetryable(e)) throw e;
        try {
          Thread.sleep(ExponentialBackoff.delayForAttempt(policy, attempt).toMillis());
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw new QwenBridgeTransportException("Commerce request interrupted", interrupted);
        }
      }
    }
  }

  /** Sends one request and returns the body of a 2xx response. */
  private String send(String method, String path, Object body) {
    try {
      var response = client.send(request(method, path, body), HttpResponse.BodyHandlers.ofString());
      return checked(response.statusCode(), response.body(), path);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new QwenBridgeTransportException("Commerce request interrupted", ex);
    } catch (IOException ex) {
      throw new QwenBridgeTransportException("Commerce transport failed", ex);
    }
  }

  private String checked(int status, String body, String path) {
    if (status >= 200 && status < 300) return body;
    QwenBridgeApiError error;
    try {
      error = mapper.readValue(body, QwenBridgeApiError.class);
      if (error.status() == 0) error = QwenBridgeApiError.ofStatus(status, path);
    } catch (IOException | RuntimeException notJson) {
      error = QwenBridgeApiError.ofStatus(status, path);
    }
    throw new QwenBridgeApiException(error);
  }

  private <T> T json(String body, Class<T> type) {
    try {
      return mapper.readValue(body, type);
    } catch (IOException ex) {
      throw new QwenBridgeTransportException("Invalid commerce response", ex);
    }
  }

  private CommerceContracts.Response response(String body) {
    var response = json(body, CommerceContracts.Response.class);
    if (response.apiVersion() != API_VERSION)
      throw new QwenBridgeIncompatibleServerException(
          "Unsupported commerce apiVersion " + response.apiVersion());
    return response;
  }

  private <T extends CommerceContracts.ProgressEvent> void stream(
      String path,
      Object request,
      Class<T> type,
      Consumer<T> handler,
      AtomicReference<Stream<String>> open) {
    HttpResponse<Stream<String>> response;
    try {
      response =
          client.send(
              request("POST", path, request, "text/event-stream"),
              HttpResponse.BodyHandlers.ofLines());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new QwenBridgeTransportException("Commerce request interrupted", ex);
    } catch (IOException ex) {
      throw new QwenBridgeTransportException("Commerce transport failed", ex);
    }
    try (var lines = response.body()) {
      open.set(lines);
      if (response.statusCode() != 200) {
        checked(response.statusCode(), String.join("\n", lines.limit(1_000).toList()), path);
      }
      StringBuilder data = new StringBuilder();
      var iterator = lines.iterator();
      long lastSequence = 0;
      while (iterator.hasNext()) {
        String line = iterator.next();
        // Comment lines (":heartbeat") and other fields are ignored, as SSE requires.
        if (line.startsWith("data:")) data.append(line.substring(5).stripLeading()).append('\n');
        if (data.length() > MAX_EVENT_BYTES)
          throw new QwenBridgeTransportException("SSE event too large", null);
        if (line.isEmpty() && !data.isEmpty()) {
          T event = parseEvent(data.toString(), type);
          data.setLength(0);
          if (event.sequence() != null) {
            if (event.sequence() <= lastSequence)
              throw new QwenBridgeIncompatibleServerException("SSE events out of order");
            lastSequence = event.sequence();
          }
          handler.accept(event);
          if (event.isTerminal()) return;
        }
      }
      throw new QwenBridgeTransportException("Commerce stream ended without terminal event", null);
    } catch (UncheckedIOException ex) {
      throw new QwenBridgeTransportException("Commerce stream interrupted", ex);
    }
  }

  private <T extends CommerceContracts.ProgressEvent> T parseEvent(String data, Class<T> type) {
    T event = json(data, type);
    if (event.version() != API_VERSION || event.kind() == null || !KINDS.contains(event.kind()))
      throw new QwenBridgeIncompatibleServerException("Unsupported commerce event");
    return event;
  }

  private HttpRequest request(String method, String path, Object body) {
    return request(method, path, body, "application/json");
  }

  /** {@code accept} is {@code text/event-stream} for streams; the server refuses others (406). */
  private HttpRequest request(String method, String path, Object body, String accept) {
    try {
      var builder =
          HttpRequest.newBuilder(config.baseUrl().resolve(path))
              .timeout(config.requestTimeout())
              .header("Accept", accept)
              .header("X-Request-Timeout-Ms", Long.toString(config.requestTimeout().toMillis()));
      if (!config.bearerToken().isEmpty())
        builder.header("Authorization", "Bearer " + config.bearerToken());
      if (body == null) return builder.method(method, HttpRequest.BodyPublishers.noBody()).build();
      return builder
          .header("Content-Type", "application/json")
          .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
          .build();
    } catch (IOException ex) {
      throw new IllegalArgumentException("Invalid commerce request", ex);
    }
  }

  private void cancelQuietly(String requestId) {
    client
        .sendAsync(
            request("DELETE", "/api/v2/requests/" + requestId, null),
            HttpResponse.BodyHandlers.discarding())
        .exceptionally(error -> null);
  }

  private static Throwable unwrap(Throwable error) {
    Throwable cause =
        error instanceof java.util.concurrent.CompletionException && error.getCause() != null
            ? error.getCause()
            : error;
    if (cause instanceof IOException io)
      return new QwenBridgeTransportException("Commerce transport failed", io);
    return cause;
  }

  @Override
  public void close() {
    client.close();
  }
}

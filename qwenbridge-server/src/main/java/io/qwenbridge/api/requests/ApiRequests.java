package io.qwenbridge.api.requests;

import io.qwenbridge.core.context.RequestScope;
import io.qwenbridge.exception.ApiExceptionMappers;
import io.qwenbridge.operations.tracing.TraceContextFilter;
import io.qwenbridge.security.ApiIdentity;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Request lifecycle for the v2 API (QB-182, QB-183, QB-184).
 *
 * <ul>
 *   <li>Every request runs in a Core {@link RequestScope} with a deadline: the configured maximum
 *       ({@code qwenbridge.api.request-timeout}), or less when the client sends {@value
 *       #TIMEOUT_HEADER}. Stages, connector guards and connectors bound their own timeouts by it.
 *   <li>At the deadline, or when the owner cancels ({@code DELETE /api/v2/requests/{id}}), the
 *       scope is marked and the working thread is interrupted, so blocking downstream calls stop.
 *       The request then fails as {@code TIMEOUT} or {@code CANCELLED}, never as a generic failure.
 *   <li>The work stays on the request thread, so request-scoped context is not lost; the interrupt
 *       flag is cleared before the thread is returned.
 * </ul>
 */
@jakarta.inject.Singleton
public final class ApiRequests {

  public static final String TIMEOUT_HEADER = "X-Request-Timeout-Ms";

  /** One in-flight request. Interrupts reach its thread only while the work is running. */
  static final class Flight {
    final RequestScope scope;
    final String subject;
    private final Thread thread;
    private boolean finished;

    Flight(RequestScope scope, String subject, Thread thread) {
      this.scope = scope;
      this.subject = subject;
      this.thread = thread;
    }

    synchronized void interrupt() {
      if (!finished) {
        thread.interrupt();
      }
    }

    void cancel() {
      scope.cancel();
      interrupt();
    }

    synchronized void finish() {
      finished = true;
    }
  }

  /** The typed error a failed stream reports (QB-180, QB-181). */
  public record StreamError(String code, String category, String message, boolean retryable) {}

  /** Builds one stream event; the record shape belongs to the endpoint. */
  @FunctionalInterface
  public interface EventFactory<R> {
    Object event(
        String kind,
        long sequence,
        boolean terminal,
        R result,
        String message,
        StreamError error,
        String traceId);
  }

  private final Duration maxTimeout;
  private final int maxPageSize;
  private final Duration heartbeat;
  private final Clock clock;
  private final Map<String, Flight> flights = new ConcurrentHashMap<>();
  private final ScheduledExecutorService timers =
      Executors.newScheduledThreadPool(
          1, Thread.ofPlatform().name("qwenbridge-request-timers").daemon().factory());

  @Inject
  public ApiRequests(
      @ConfigProperty(name = "qwenbridge.api.request-timeout", defaultValue = "60s")
          Duration maxTimeout,
      @ConfigProperty(name = "qwenbridge.api.max-page-size", defaultValue = "100") int maxPageSize,
      @ConfigProperty(name = "qwenbridge.api.sse.heartbeat", defaultValue = "15s")
          Duration heartbeat) {
    this(maxTimeout, maxPageSize, heartbeat, Clock.systemUTC());
  }

  public ApiRequests(Duration maxTimeout, int maxPageSize, Duration heartbeat, Clock clock) {
    if (maxTimeout.isNegative()
        || maxTimeout.isZero()
        || maxTimeout.compareTo(Duration.ofMinutes(10)) > 0)
      throw new IllegalArgumentException("qwenbridge.api.request-timeout must be 1ms..10m");
    if (maxPageSize < 1 || maxPageSize > 100)
      throw new IllegalArgumentException("qwenbridge.api.max-page-size must be 1..100");
    if (heartbeat.compareTo(Duration.ofSeconds(1)) < 0)
      throw new IllegalArgumentException("qwenbridge.api.sse.heartbeat must be at least 1s");
    this.maxTimeout = maxTimeout;
    this.maxPageSize = maxPageSize;
    this.heartbeat = heartbeat;
    this.clock = clock;
    // QB-212: requests currently executing under a deadline (active concurrency).
    io.micrometer.core.instrument.Metrics.globalRegistry.gauge(
        "qwenbridge.api.inflight", flights, Map::size);
  }

  public Duration maxTimeout() {
    return maxTimeout;
  }

  public int maxPageSize() {
    return maxPageSize;
  }

  /** The request's time budget: the client's, never more than the configured maximum. */
  public Duration timeout(String header) {
    if (header == null || header.isBlank()) return maxTimeout;
    long millis;
    try {
      millis = Long.parseLong(header.trim());
    } catch (NumberFormatException e) {
      millis = -1;
    }
    if (millis < 1)
      throw new IllegalArgumentException(
          TIMEOUT_HEADER + " must be a positive number of milliseconds");
    return Duration.ofMillis(Math.min(millis, maxTimeout.toMillis()));
  }

  /** QB-183: page sizes above the configured maximum are refused, not silently truncated. */
  public void requirePageSize(Integer limit) {
    if (limit != null && limit > maxPageSize)
      throw new IllegalArgumentException("Page size must be at most " + maxPageSize);
  }

  /** Runs {@code work} as the request {@code requestId} of {@code caller}, on this thread. */
  public <T> T run(
      ContainerRequestContext context,
      ApiIdentity.Principal caller,
      String requestId,
      Supplier<T> work) {
    return run(
        caller,
        requestId,
        TraceContextFilter.traceparent(context).orElse(null),
        timeout(context.getHeaderString(TIMEOUT_HEADER)),
        work,
        null);
  }

  <T> T run(
      ApiIdentity.Principal caller,
      String requestId,
      String traceparent,
      Duration timeout,
      Supplier<T> work,
      java.util.function.Consumer<Flight> started) {
    var scope = RequestScope.create(requestId, traceparent, clock.instant().plus(timeout), clock);
    String key = key(caller.tenant(), requestId);
    var flight = new Flight(scope, caller.subject(), Thread.currentThread());
    if (flights.putIfAbsent(key, flight) != null)
      throw new ClientErrorException("Request " + requestId + " is already in flight", 409);
    ScheduledFuture<?> timer =
        timers.schedule(flight::interrupt, timeout.toMillis(), TimeUnit.MILLISECONDS);
    try (var ignored = scope.enter()) {
      if (started != null) started.accept(flight);
      T result = work.get();
      // Work that noticed the stop may still return (a degraded or partial result): report the
      // stop instead, so a cancelled or expired request never looks complete.
      scope.checkpoint();
      return result;
    } catch (RequestScope.Interrupted e) {
      io.qwenbridge.operations.metrics.ApiMetrics.interruption(e.reason().name());
      throw e;
    } catch (RuntimeException e) {
      Optional<RequestScope.Interruption> why = scope.interruption();
      if (why.isPresent()) {
        io.qwenbridge.operations.metrics.ApiMetrics.interruption(why.get().name());
        var stopped = new RequestScope.Interrupted(why.get());
        stopped.addSuppressed(e);
        throw stopped;
      }
      throw e;
    } finally {
      flight.finish();
      timer.cancel(false);
      flights.remove(key, flight);
      scope.close();
      // An interrupt that arrived after the work ended must not leak into the next request.
      Thread.interrupted();
    }
  }

  /**
   * Cancels the caller's own in-flight request. Requests of other tenants or principals are
   * reported as unknown.
   */
  public boolean cancel(ApiIdentity.Principal caller, String requestId) {
    Flight flight = flights.get(key(caller.tenant(), requestId));
    if (flight == null || !flight.subject.equals(caller.subject())) return false;
    flight.cancel();
    return true;
  }

  /**
   * Runs {@code work} as a v2 progress stream (QB-180): one {@code <prefix>.started} event, SSE
   * comment heartbeats while the work runs, and exactly one terminal event, {@code
   * <prefix>.completed} or {@code <prefix>.failed} (with a typed error, {@code CANCELLED} and
   * {@code TIMEOUT} included). A heartbeat that cannot be delivered means the client is gone, and
   * the request is cancelled.
   */
  public <R> void stream(
      ContainerRequestContext context,
      ApiIdentity.Principal caller,
      String requestId,
      String prefix,
      String failureMessage,
      SseEventSink sink,
      Sse sse,
      EventFactory<R> events,
      Supplier<R> work) {
    String traceparent = TraceContextFilter.traceparent(context).orElse(null);
    String traceId = traceparent == null ? null : traceparent.substring(3, 35);
    Duration timeout = timeout(context.getHeaderString(TIMEOUT_HEADER));
    var sequence = new AtomicLong();
    Object lock = new Object();
    boolean[] done = {false};
    ScheduledFuture<?>[] beats = {null};
    try {
      send(
          sink,
          sse,
          prefix + ".started",
          events.event(
              prefix + ".started", sequence.incrementAndGet(), false, null, "", null, traceId));
      R result =
          run(
              caller,
              requestId,
              traceparent,
              timeout,
              work,
              flight ->
                  beats[0] =
                      timers.scheduleAtFixedRate(
                          () -> {
                            synchronized (lock) {
                              if (done[0]) return;
                              if (sink.isClosed()) {
                                flight.cancel();
                                return;
                              }
                              sink.send(sse.newEventBuilder().comment("heartbeat").build())
                                  .whenComplete(
                                      (ok, error) -> {
                                        if (error != null) flight.cancel();
                                      });
                            }
                          },
                          heartbeat.toMillis(),
                          heartbeat.toMillis(),
                          TimeUnit.MILLISECONDS));
      stopHeartbeat(lock, done, beats);
      send(
          sink,
          sse,
          prefix + ".completed",
          events.event(
              prefix + ".completed", sequence.incrementAndGet(), true, result, "", null, traceId));
    } catch (RuntimeException ex) {
      stopHeartbeat(lock, done, beats);
      if (!sink.isClosed()) {
        var described = ApiExceptionMappers.describe(ex);
        io.qwenbridge.operations.metrics.ApiMetrics.error("v2-sse", described.code());
        var error =
            new StreamError(
                described.code().name(),
                described.code().category().name(),
                described.message(),
                described.code().category().retryable());
        try {
          send(
              sink,
              sse,
              prefix + ".failed",
              events.event(
                  prefix + ".failed",
                  sequence.incrementAndGet(),
                  true,
                  null,
                  failureMessage,
                  error,
                  traceId));
        } catch (RuntimeException ignored) {
          // The client is gone; there is nobody left to tell.
        }
      }
    } finally {
      stopHeartbeat(lock, done, beats);
      sink.close();
    }
  }

  private static void stopHeartbeat(Object lock, boolean[] done, ScheduledFuture<?>[] beats) {
    synchronized (lock) {
      done[0] = true;
      if (beats[0] != null) beats[0].cancel(false);
    }
  }

  private static void send(SseEventSink sink, Sse sse, String name, Object data) {
    sink.send(
            sse.newEventBuilder()
                .name(name)
                .mediaType(MediaType.APPLICATION_JSON_TYPE)
                .data(data)
                .build())
        .toCompletableFuture()
        .join();
  }

  private static String key(String tenant, String requestId) {
    return tenant + '\u0000' + requestId;
  }

  @PreDestroy
  void shutdown() {
    timers.shutdownNow();
  }
}

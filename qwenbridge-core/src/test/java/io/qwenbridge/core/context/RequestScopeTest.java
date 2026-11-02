package io.qwenbridge.core.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.core.connector.CircuitBreaker;
import io.qwenbridge.core.connector.ConnectorResult;
import io.qwenbridge.core.connector.Resilient;
import io.qwenbridge.core.connector.RetryPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** QB-182 / QB-184: request scope, deadlines, cancellation and trace propagation. */
class RequestScopeTest {

  static final String TRACE = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";

  @Test
  void aScopeIsCurrentOnlyWhileEnteredAndNeverAfterItIsClosed() {
    var scope = RequestScope.create("req-1", TRACE, null, Clock.systemUTC());
    assertTrue(RequestScope.current().isEmpty());
    try (var ignored = scope.enter()) {
      assertEquals(Optional.of(scope), RequestScope.current());
      var inner = RequestScope.create("req-2", null, null, Clock.systemUTC());
      try (var nested = inner.enter()) {
        assertEquals("req-2", RequestScope.current().orElseThrow().requestId());
      }
      assertEquals("req-1", RequestScope.current().orElseThrow().requestId());
      scope.close();
      assertTrue(RequestScope.current().isEmpty(), "a closed scope is never current");
    }
    assertTrue(RequestScope.current().isEmpty());
    assertThrows(
        IllegalArgumentException.class,
        () -> RequestScope.create("bad id\n", null, null, Clock.systemUTC()));
  }

  @Test
  void deadlinesBoundTimeoutsAndCancellationWinsOverExpiry() {
    var now = Instant.parse("2026-11-01T12:00:00Z");
    var clock = Clock.fixed(now, ZoneOffset.UTC);
    var scope = RequestScope.create("req", null, now.plusMillis(250), clock);
    assertEquals(Duration.ofMillis(250), scope.bound(Duration.ofSeconds(5)));
    assertEquals(Duration.ofMillis(100), scope.bound(Duration.ofMillis(100)));
    assertFalse(scope.expired());
    scope.checkpoint();

    var expired = RequestScope.create("req", null, now.minusMillis(1), clock);
    assertEquals(Duration.ZERO, expired.remaining().orElseThrow());
    var error = assertThrows(RequestScope.Interrupted.class, expired::checkpoint);
    assertEquals(RequestScope.Interruption.DEADLINE_EXCEEDED, error.reason());
    expired.cancel();
    assertEquals(Optional.of(RequestScope.Interruption.CANCELLED), expired.interruption());
    assertTrue(expired.cancellation().cancelled(), "retrieval providers see the cancellation");

    var unbounded = RequestScope.create("req", null, null, clock);
    assertEquals(Duration.ofSeconds(5), unbounded.bound(Duration.ofSeconds(5)));
    try (var ignored = unbounded.enter()) {
      unbounded.cancel();
      assertThrows(RequestScope.Interrupted.class, RequestScope::checkpointCurrent);
    }
  }

  @Test
  void outboundTraceparentsKeepTheTraceAndFlagsWithANewSpan() {
    var scope = RequestScope.create("req", TRACE, null, Clock.systemUTC());
    String outbound = scope.outboundTraceparent().orElseThrow();
    assertTrue(RequestScope.validTraceparent(outbound));
    assertEquals(TRACE.substring(0, 36), outbound.substring(0, 36));
    assertEquals("01", outbound.substring(53));
    assertNotEquals(TRACE, outbound);
    // Invalid or all-zero trace parents are dropped, never forwarded.
    for (String bad :
        new String[] {
          "00-00000000000000000000000000000000-b7ad6b7169203331-01",
          "00-0af7651916cd43dd8448eb211c80319c-0000000000000000-01",
          "00-0AF7651916CD43DD8448EB211C80319C-b7ad6b7169203331-01",
          "ff-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01\r\nX: y"
        }) {
      assertTrue(
          RequestScope.create("req", bad, null, Clock.systemUTC()).outboundTraceparent().isEmpty(),
          bad);
    }
  }

  @Test
  void workHandedToAnotherThreadRunsInTheScopeOnlyWhenPropagated() throws Exception {
    var scope = RequestScope.create("req-x", null, null, Clock.systemUTC());
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var seen = executor.submit(scope.propagate(() -> RequestScope.current())).get();
      assertEquals("req-x", seen.orElseThrow().requestId());
      try (var ignored = scope.enter()) {
        var plain = executor.submit(() -> RequestScope.current()).get();
        assertTrue(plain.isEmpty(), "never inherited implicitly");
      }
    }
  }

  @Test
  void connectorAttemptsAreBoundedByTheRequestAndCallerStopsNeverOpenTheBreaker() throws Exception {
    var breaker = new CircuitBreaker(1, Duration.ofMinutes(1), Clock.systemUTC());
    var deadline =
        RequestScope.create("req", null, Instant.now().plusMillis(150), Clock.systemUTC());
    long started = System.nanoTime();
    ConnectorResult<String> late;
    AtomicReference<String> seenRequest = new AtomicReference<>();
    try (var ignored = deadline.enter()) {
      late =
          Resilient.call(
              "slow",
              () -> {
                seenRequest.set(RequestScope.current().map(RequestScope::requestId).orElse(""));
                Thread.sleep(10_000);
                return "late";
              },
              Duration.ofSeconds(30),
              RetryPolicy.NONE,
              breaker,
              d -> {});
    }
    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    assertEquals(
        ConnectorResult.Failure.DEADLINE_EXCEEDED, ((ConnectorResult.Failed<?>) late).failure());
    assertTrue(elapsedMs < 5_000, "stopped at the request deadline, took " + elapsedMs + " ms");
    assertEquals("req", seenRequest.get(), "the attempt runs in the request scope");
    assertEquals(CircuitBreaker.State.CLOSED, breaker.state(), "not the connector's failure");

    // Already cancelled: never attempted.
    var cancelled = RequestScope.create("req", null, null, Clock.systemUTC());
    cancelled.cancel();
    try (var ignored = cancelled.enter()) {
      var result =
          Resilient.call("c", () -> "x", Duration.ofSeconds(1), RetryPolicy.NONE, breaker, d -> {});
      assertEquals(
          new ConnectorResult.Failed<String>(ConnectorResult.Failure.CANCELLED, "c", 0), result);
    }

    // Cancelled while waiting: the waiting thread is interrupted, the attempt is cancelled.
    var waiting = RequestScope.create("req", null, null, Clock.systemUTC());
    var entered = new CountDownLatch(1);
    var interrupted = new CountDownLatch(1);
    var outcome = new AtomicReference<ConnectorResult<String>>();
    Thread caller =
        Thread.ofVirtual()
            .start(
                () -> {
                  try (var ignored = waiting.enter()) {
                    outcome.set(
                        Resilient.call(
                            "c",
                            () -> {
                              entered.countDown();
                              try {
                                Thread.sleep(10_000);
                              } catch (InterruptedException e) {
                                interrupted.countDown();
                                throw e;
                              }
                              return "late";
                            },
                            Duration.ofSeconds(30),
                            RetryPolicy.NONE,
                            breaker,
                            d -> {}));
                  }
                });
    assertTrue(entered.await(5, TimeUnit.SECONDS));
    waiting.cancel();
    caller.interrupt();
    caller.join(5_000);
    assertEquals(
        ConnectorResult.Failure.CANCELLED, ((ConnectorResult.Failed<?>) outcome.get()).failure());
    assertTrue(interrupted.await(5, TimeUnit.SECONDS), "the attempt itself was stopped");
    assertEquals(CircuitBreaker.State.CLOSED, breaker.state());
  }
}

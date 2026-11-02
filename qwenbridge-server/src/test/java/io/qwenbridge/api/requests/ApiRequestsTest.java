package io.qwenbridge.api.requests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.qwenbridge.core.context.RequestScope;
import io.qwenbridge.security.ApiIdentity;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** QB-182 / QB-183: request deadlines, cancellation and limits, without a server. */
class ApiRequestsTest {

  final ApiRequests api =
      new ApiRequests(Duration.ofSeconds(30), 50, Duration.ofSeconds(15), Clock.systemUTC());
  final ApiIdentity.Principal alice = new ApiIdentity.Principal("alice", "shopA", Set.of("search"));
  final ApiIdentity.Principal mallory =
      new ApiIdentity.Principal("mallory", "shopA", Set.of("search"));

  @Test
  void clientTimeoutsAreValidatedAndNeverExceedTheConfiguredMaximum() {
    assertThat(api.timeout(null)).isEqualTo(Duration.ofSeconds(30));
    assertThat(api.timeout("250")).isEqualTo(Duration.ofMillis(250));
    assertThat(api.timeout("999999999")).isEqualTo(Duration.ofSeconds(30));
    for (String bad : new String[] {"0", "-5", "soon", "1.5"})
      assertThatThrownBy(() -> api.timeout(bad))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(ApiRequests.TIMEOUT_HEADER);
    api.requirePageSize(null);
    api.requirePageSize(50);
    assertThatThrownBy(() -> api.requirePageSize(51)).hasMessageContaining("at most 50");
    assertThatThrownBy(
            () ->
                new ApiRequests(
                    Duration.ofSeconds(1), 101, Duration.ofSeconds(1), Clock.systemUTC()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void workRunsInAScopeAndAnExpiredRequestIsInterruptedAndReportedAsTimeout() {
    String seen =
        api.run(
            alice,
            "r-1",
            null,
            Duration.ofSeconds(5),
            () -> RequestScope.current().orElseThrow().requestId(),
            null);
    assertThat(seen).isEqualTo("r-1");
    long started = System.nanoTime();
    assertThatThrownBy(
            () ->
                api.run(
                    alice,
                    "r-2",
                    null,
                    Duration.ofMillis(200),
                    () -> {
                      try {
                        Thread.sleep(20_000);
                      } catch (InterruptedException e) {
                        // Swallowing the interrupt still cannot make the request look complete.
                        return "partial";
                      }
                      return "late";
                    },
                    null))
        .isInstanceOfSatisfying(
            RequestScope.Interrupted.class,
            e -> assertThat(e.reason()).isEqualTo(RequestScope.Interruption.DEADLINE_EXCEEDED));
    assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(5_000);
    assertThat(Thread.currentThread().isInterrupted())
        .as("the interrupt never leaks into the next request on this thread")
        .isFalse();
    assertThat(RequestScope.current()).isEmpty();
  }

  @Test
  void onlyTheOwnerCancelsAndRequestIdsAreUniqueWhileInFlight() throws Exception {
    var entered = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var running =
          executor.submit(
              () ->
                  api.run(
                      alice,
                      "shared-id",
                      null,
                      Duration.ofSeconds(20),
                      () -> {
                        entered.countDown();
                        try {
                          Thread.sleep(20_000);
                        } catch (InterruptedException e) {
                          throw new IllegalStateException("backend interrupted");
                        }
                        return "late";
                      },
                      null));
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      assertThatThrownBy(
              () -> api.run(alice, "shared-id", null, Duration.ofSeconds(1), () -> "x", null))
          .isInstanceOf(jakarta.ws.rs.ClientErrorException.class)
          .hasMessageContaining("already in flight");
      assertThat(api.cancel(mallory, "shared-id")).as("same tenant, other principal").isFalse();
      assertThat(api.cancel(new ApiIdentity.Principal("alice", "shopB", Set.of()), "shared-id"))
          .isFalse();
      assertThat(api.cancel(alice, "shared-id")).isTrue();
      var failure =
          org.junit.jupiter.api.Assertions.assertThrows(
              java.util.concurrent.ExecutionException.class,
              () -> running.get(5, TimeUnit.SECONDS));
      assertThat(failure.getCause())
          .isInstanceOfSatisfying(
              RequestScope.Interrupted.class,
              e -> assertThat(e.reason()).isEqualTo(RequestScope.Interruption.CANCELLED));
    }
    assertThat(api.cancel(alice, "shared-id")).as("finished requests are gone").isFalse();
  }
}

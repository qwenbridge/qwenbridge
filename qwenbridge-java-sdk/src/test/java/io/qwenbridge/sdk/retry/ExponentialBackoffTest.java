package io.qwenbridge.sdk.retry;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ExponentialBackoffTest {

  @Test
  void shouldCalculateExponentialDelay() {
    RetryPolicy policy = new RetryPolicy(5, Duration.ofMillis(100), Duration.ofSeconds(5));

    assertEquals(Duration.ofMillis(100), ExponentialBackoff.delayForAttempt(policy, 1));
    assertEquals(Duration.ofMillis(200), ExponentialBackoff.delayForAttempt(policy, 2));
    assertEquals(Duration.ofMillis(400), ExponentialBackoff.delayForAttempt(policy, 3));
  }

  @Test
  void shouldCapDelayAtMaxBackoff() {
    RetryPolicy policy = new RetryPolicy(10, Duration.ofMillis(500), Duration.ofSeconds(1));

    assertEquals(Duration.ofMillis(500), ExponentialBackoff.delayForAttempt(policy, 1));
    assertEquals(Duration.ofSeconds(1), ExponentialBackoff.delayForAttempt(policy, 2));
    assertEquals(Duration.ofSeconds(1), ExponentialBackoff.delayForAttempt(policy, 5));
  }

  @Test
  void shouldRejectInvalidAttempt() {
    RetryPolicy policy = RetryPolicy.defaultPolicy();

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> ExponentialBackoff.delayForAttempt(policy, 0));

    assertEquals("attempt must be at least 1", exception.getMessage());
  }
}

package io.qwenbridge.core.connector;

import java.time.Duration;
import java.util.function.Predicate;

/**
 * Bounded retry (QB-158): at most {@code maxAttempts} attempts in total, only for failures {@code
 * retryable} accepts, with a linear backoff. Writes should use {@link #NONE}: retrying a write that
 * may have happened is not safe.
 */
public record RetryPolicy(int maxAttempts, Duration backoff, Predicate<Throwable> retryable) {
  public static final RetryPolicy NONE = new RetryPolicy(1, Duration.ZERO, t -> false);

  public RetryPolicy {
    if (maxAttempts < 1 || maxAttempts > 5) {
      throw new IllegalArgumentException("maxAttempts must be within [1, 5]");
    }
    if (backoff == null || backoff.isNegative() || backoff.compareTo(Duration.ofSeconds(5)) > 0) {
      throw new IllegalArgumentException("backoff must be within [0, 5s]");
    }
    if (retryable == null) {
      throw new IllegalArgumentException("retryable must not be null");
    }
  }
}

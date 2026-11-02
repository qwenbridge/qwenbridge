package io.qwenbridge.core.connector;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * A circuit breaker (QB-158). After {@code failureThreshold} consecutive failures it opens and
 * refuses calls for {@code openDuration}; then it lets one trial call through (half-open). A
 * success closes it; a failure opens it again. Thread-safe.
 */
public final class CircuitBreaker {
  public enum State {
    CLOSED,
    OPEN,
    HALF_OPEN
  }

  private final int failureThreshold;
  private final Duration openDuration;
  private final Clock clock;
  private State state = State.CLOSED;
  private int failures;
  private Instant openedAt;
  private boolean trialInFlight;

  public CircuitBreaker(int failureThreshold, Duration openDuration, Clock clock) {
    if (failureThreshold < 1 || failureThreshold > 100) {
      throw new IllegalArgumentException("failureThreshold must be within [1, 100]");
    }
    if (openDuration.isNegative() || openDuration.isZero()) {
      throw new IllegalArgumentException("openDuration must be positive");
    }
    this.failureThreshold = failureThreshold;
    this.openDuration = openDuration;
    this.clock = clock;
  }

  /** Whether a call may proceed now. */
  public synchronized boolean tryAcquire() {
    if (state == State.OPEN && !clock.instant().isBefore(openedAt.plus(openDuration))) {
      state = State.HALF_OPEN;
      trialInFlight = false;
    }
    return switch (state) {
      case CLOSED -> true;
      case OPEN -> false;
      case HALF_OPEN -> {
        if (trialInFlight) {
          yield false;
        }
        trialInFlight = true;
        yield true;
      }
    };
  }

  public synchronized void onSuccess() {
    state = State.CLOSED;
    failures = 0;
    trialInFlight = false;
  }

  /**
   * The acquired call ended without a verdict on the connector (the caller cancelled or ran out of
   * time): a half-open trial slot is freed, and nothing else changes.
   */
  public synchronized void release() {
    trialInFlight = false;
  }

  public synchronized void onFailure() {
    trialInFlight = false;
    if (state == State.HALF_OPEN || ++failures >= failureThreshold) {
      state = State.OPEN;
      openedAt = clock.instant();
      failures = 0;
    }
  }

  public synchronized State state() {
    if (state == State.OPEN && !clock.instant().isBefore(openedAt.plus(openDuration))) {
      return State.HALF_OPEN;
    }
    return state;
  }
}

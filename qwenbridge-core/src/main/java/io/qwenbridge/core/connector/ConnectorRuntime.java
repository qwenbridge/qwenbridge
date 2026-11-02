package io.qwenbridge.core.connector;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Owns one connector (QB-153, QB-157, QB-158): validates its configuration, drives its lifecycle,
 * checks access before every operation and runs operations through the resilience guard. Nothing a
 * connector throws escapes as an exception: start failures become FAILED with a reason, operation
 * failures become typed results.
 */
public final class ConnectorRuntime {

  /** One lifecycle change and why. */
  public record Transition(ConnectorState from, ConnectorState to, String reason, Instant at) {}

  private final Connector connector;
  private final Clock clock;
  private final CircuitBreaker breaker;
  private final Duration timeout;
  private final Resilient.Sleeper sleeper;
  private final Deque<Transition> history = new ArrayDeque<>();
  private ConnectorState state;
  private String reason = "";

  public ConnectorRuntime(
      Connector connector,
      Clock clock,
      CircuitBreaker breaker,
      Duration timeout,
      Resilient.Sleeper sleeper) {
    this.connector = Objects.requireNonNull(connector, "connector must not be null");
    this.clock = clock;
    this.breaker = breaker;
    this.timeout = timeout;
    this.sleeper = sleeper;
    this.state = ConnectorState.CREATED;
  }

  public String id() {
    return connector.id();
  }

  public ConnectorCapabilities capabilities() {
    return connector.capabilities();
  }

  public synchronized ConnectorState state() {
    return state;
  }

  public synchronized String reason() {
    return reason;
  }

  public synchronized List<Transition> history() {
    return List.copyOf(history);
  }

  public CircuitBreaker.State circuit() {
    return breaker.state();
  }

  private synchronized void move(ConnectorState next, String why) {
    if (!state.canMoveTo(next)) {
      throw new IllegalStateException(state + " cannot move to " + next);
    }
    history.addLast(new Transition(state, next, why, clock.instant()));
    if (history.size() > 20) {
      history.removeFirst();
    }
    state = next;
    reason = why;
  }

  /**
   * Starts from a configuration. A disabled configuration leaves the connector DISABLED; an invalid
   * one, or a failing start, leaves it FAILED with the reason. Never throws.
   */
  public synchronized ConnectorState start(ConnectorConfig config, SecretResolver secrets) {
    if (!config.enabled()) {
      history.addLast(
          new Transition(state, ConnectorState.DISABLED, "not configured", clock.instant()));
      state = ConnectorState.DISABLED;
      reason = "not configured";
      return state;
    }
    move(ConnectorState.STARTING, "start requested");
    var problems = config.problems(connector.configSchema());
    if (!problems.isEmpty()) {
      move(ConnectorState.FAILED, "invalid configuration: " + problems);
      return state;
    }
    try {
      connector.start(config, secrets);
      move(connector.healthy() ? ConnectorState.READY : ConnectorState.DEGRADED, "started");
    } catch (Exception e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      // The exception type only: messages may carry connection strings.
      move(ConnectorState.FAILED, "start failed: " + e.getClass().getSimpleName());
    }
    return state;
  }

  public synchronized void stop() {
    if (state == ConnectorState.DISABLED
        || state == ConnectorState.STOPPED
        || state == ConnectorState.CREATED) {
      return;
    }
    move(ConnectorState.STOPPING, "stop requested");
    try {
      connector.stop();
    } catch (RuntimeException ignored) {
      // A connector that fails to stop is still stopped for the platform.
    }
    move(ConnectorState.STOPPED, "stopped");
  }

  /** Runs an operation that needs {@code access}, with timeout, {@code retry} and the breaker. */
  public <T> ConnectorResult<T> execute(
      ConnectorCapabilities.Access access, RetryPolicy retry, Callable<T> operation) {
    ConnectorState now = state();
    if (!now.acceptsOperations()) {
      return new ConnectorResult.Failed<>(ConnectorResult.Failure.NOT_READY, id(), 0);
    }
    if (!connector.capabilities().allows(access)) {
      return new ConnectorResult.Failed<>(ConnectorResult.Failure.ACCESS_DENIED, id(), 0);
    }
    ConnectorResult<T> result = Resilient.call(id(), operation, timeout, retry, breaker, sleeper);
    synchronized (this) {
      if (breaker.state() == CircuitBreaker.State.OPEN && state == ConnectorState.READY) {
        move(ConnectorState.DEGRADED, "circuit open after repeated failures");
      } else if (result instanceof ConnectorResult.Success<T>
          && state == ConnectorState.DEGRADED
          && breaker.state() == CircuitBreaker.State.CLOSED) {
        move(ConnectorState.READY, "recovered");
      }
    }
    return result;
  }
}

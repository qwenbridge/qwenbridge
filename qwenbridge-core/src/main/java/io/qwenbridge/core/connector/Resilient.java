package io.qwenbridge.core.connector;

import io.qwenbridge.core.context.RequestScope;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs one connector call with a timeout per attempt, bounded retry and a circuit breaker (QB-158).
 * A call refused by an open breaker is never attempted. Timeouts cancel the attempt (its thread is
 * interrupted). Only the final outcome counts towards the breaker.
 *
 * <p>Inside a {@link RequestScope} (QB-182) each attempt runs in that scope, its timeout is bounded
 * by the request's remaining time, and no attempt starts once the request was cancelled or expired.
 * Stopping for the caller's sake ({@code CANCELLED}, {@code DEADLINE_EXCEEDED}) is not a failure of
 * the connector and never counts towards the breaker.
 */
public final class Resilient {

  /** Sleeps between attempts; replaceable in tests. */
  @FunctionalInterface
  public interface Sleeper {
    void sleep(Duration duration) throws InterruptedException;
  }

  private Resilient() {}

  public static <T> ConnectorResult<T> call(
      String connector,
      Callable<T> call,
      Duration timeout,
      RetryPolicy retry,
      CircuitBreaker breaker,
      Sleeper sleeper) {
    Optional<RequestScope> scope = RequestScope.current();
    Optional<ConnectorResult.Failure> stopped = stopped(scope);
    if (stopped.isPresent()) {
      return new ConnectorResult.Failed<>(stopped.get(), connector, 0);
    }
    if (!breaker.tryAcquire()) {
      return new ConnectorResult.Failed<>(ConnectorResult.Failure.CIRCUIT_OPEN, connector, 0);
    }
    Callable<T> attemptCall = scope.isPresent() ? scope.get().propagate(call) : call;
    ConnectorResult.Failure last = ConnectorResult.Failure.FAILED;
    int attempt = 0;
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      while (++attempt <= retry.maxAttempts()) {
        stopped = stopped(scope);
        if (stopped.isPresent()) {
          breaker.release();
          return new ConnectorResult.Failed<>(stopped.get(), connector, attempt - 1);
        }
        Duration bounded = scope.isPresent() ? scope.get().bound(timeout) : timeout;
        Future<T> future = executor.submit(attemptCall);
        try {
          T value = future.get(Math.max(1, bounded.toMillis()), TimeUnit.MILLISECONDS);
          breaker.onSuccess();
          return new ConnectorResult.Success<>(value, attempt);
        } catch (TimeoutException e) {
          future.cancel(true);
          if (bounded.compareTo(timeout) < 0) {
            // The request's deadline, not the connector, ended this attempt.
            breaker.release();
            return new ConnectorResult.Failed<>(
                ConnectorResult.Failure.DEADLINE_EXCEEDED, connector, attempt);
          }
          last = ConnectorResult.Failure.TIMEOUT;
          if (!retry.retryable().test(e)) {
            break;
          }
        } catch (ExecutionException e) {
          last = ConnectorResult.Failure.FAILED;
          if (!retry.retryable().test(e.getCause())) {
            break;
          }
        } catch (InterruptedException e) {
          // The caller gave up (a cancelled or expired request): stop the attempt, blame no one.
          Thread.currentThread().interrupt();
          future.cancel(true);
          breaker.release();
          return new ConnectorResult.Failed<>(
              stopped(scope).orElse(ConnectorResult.Failure.CANCELLED), connector, attempt);
        }
        if (attempt < retry.maxAttempts()) {
          try {
            sleeper.sleep(retry.backoff().multipliedBy(attempt));
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            breaker.release();
            return new ConnectorResult.Failed<>(
                stopped(scope).orElse(ConnectorResult.Failure.CANCELLED), connector, attempt);
          }
        }
      }
    }
    breaker.onFailure();
    return new ConnectorResult.Failed<>(last, connector, Math.min(attempt, retry.maxAttempts()));
  }

  private static Optional<ConnectorResult.Failure> stopped(Optional<RequestScope> scope) {
    return scope
        .flatMap(RequestScope::interruption)
        .map(
            reason ->
                reason == RequestScope.Interruption.CANCELLED
                    ? ConnectorResult.Failure.CANCELLED
                    : ConnectorResult.Failure.DEADLINE_EXCEEDED);
  }
}

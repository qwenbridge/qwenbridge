package io.qwenbridge.core.connector;

import java.util.Objects;

/** The typed outcome of a connector operation (QB-153, QB-158): a value, or why there is none. */
public sealed interface ConnectorResult<T> {

  enum Failure {
    /** The connector is not in a state that accepts operations. */
    NOT_READY,
    /** The connector does not have the access the operation needs (read-only, no delete, …). */
    ACCESS_DENIED,
    /** The circuit breaker is open: recent calls failed, so this one was not attempted. */
    CIRCUIT_OPEN,
    TIMEOUT,
    /** The caller cancelled the request (QB-182); not a failure of the connector. */
    CANCELLED,
    /** The request's deadline ran out (QB-182); not a failure of the connector. */
    DEADLINE_EXCEEDED,
    /** The call failed (after any permitted retries). */
    FAILED
  }

  record Success<T>(T value, int attempts) implements ConnectorResult<T> {}

  record Failed<T>(Failure failure, String connector, int attempts) implements ConnectorResult<T> {
    public Failed {
      Objects.requireNonNull(failure, "failure must not be null");
      Objects.requireNonNull(connector, "connector must not be null");
    }
  }
}

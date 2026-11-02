package io.qwenbridge.intelligence.planning;

import java.util.List;
import java.util.Objects;

/** Rejects invalid input instead of silently dropping unsafe constraints. */
public final class QueryPlanningException extends IllegalArgumentException {
  private final List<String> errors;

  public QueryPlanningException(List<String> errors) {
    super("query planning rejected: " + Objects.requireNonNull(errors, "errors"));
    if (errors.isEmpty()) throw new IllegalArgumentException("errors must not be empty");
    this.errors = List.copyOf(errors);
  }

  public List<String> errors() {
    return errors;
  }
}

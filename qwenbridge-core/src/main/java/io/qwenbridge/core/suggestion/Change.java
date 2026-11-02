package io.qwenbridge.core.suggestion;

import io.qwenbridge.core.queryir.Constraint.Operator;
import io.qwenbridge.core.retrieval.filter.FieldValue;
import java.util.Objects;
import java.util.Optional;

/**
 * One typed change to a query's constraints (QB-132, QB-133). ADD adds a constraint; REPLACE
 * replaces the constraints on {@code field} with the given operator; REMOVE removes the constraints
 * on {@code field} with that operator. Nothing else in the query changes.
 */
public record Change(Op op, String field, Operator operator, Optional<FieldValue> value) {
  public enum Op {
    ADD,
    REPLACE,
    REMOVE
  }

  public Change {
    Objects.requireNonNull(op, "op must not be null");
    Objects.requireNonNull(field, "field must not be null");
    Objects.requireNonNull(operator, "operator must not be null");
    value = value == null ? Optional.empty() : value;
    if (!field.matches("[A-Za-z_][A-Za-z0-9_.]{0,63}")) {
      throw new IllegalArgumentException("invalid field");
    }
    if (op != Op.REMOVE && value.isEmpty() && !operator.valueless()) {
      throw new IllegalArgumentException("a value is required");
    }
  }

  public static Change add(String field, Operator operator, FieldValue value) {
    return new Change(Op.ADD, field, operator, Optional.of(value));
  }
}

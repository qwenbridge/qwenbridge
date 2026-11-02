package io.qwenbridge.core.schema;

import io.qwenbridge.core.queryir.Constraint.Operator;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A queryable field of a domain schema (QB-044, QB-045). It describes meaning, not storage: backend
 * column or index names belong to connectors, never to the schema.
 *
 * @param operators operators allowed on this field (the QueryIR operator set, no separate enum)
 * @param nullable whether the field may be absent; EXISTS/NOT_EXISTS are only meaningful then
 * @param unit required unit for {@link FieldType#QUANTITY}, e.g. {@code %} or {@code ms}
 * @param min inclusive lower bound for numeric values, when one exists
 * @param max inclusive upper bound for numeric values, when one exists
 * @param allowedValues closed vocabulary for TEXT/IDENTIFIER fields; empty means open
 */
public record FieldDefinition(
    String name,
    FieldType type,
    Set<Operator> operators,
    boolean nullable,
    Optional<String> unit,
    Optional<BigDecimal> min,
    Optional<BigDecimal> max,
    Set<String> allowedValues) {

  public FieldDefinition {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(type, "type must not be null");
    if (operators == null || operators.isEmpty()) {
      throw new IllegalArgumentException("a field needs at least one operator");
    }
    operators = Set.copyOf(EnumSet.copyOf(operators));
    unit = unit == null ? Optional.empty() : unit;
    min = min == null ? Optional.empty() : min;
    max = max == null ? Optional.empty() : max;
    allowedValues = allowedValues == null ? Set.of() : Set.copyOf(allowedValues);
    if (type == FieldType.QUANTITY && unit.isEmpty()) {
      throw new IllegalArgumentException("a QUANTITY field needs a unit");
    }
    if (type != FieldType.QUANTITY && unit.isPresent()) {
      throw new IllegalArgumentException("only QUANTITY fields have a unit");
    }
    if ((min.isPresent() || max.isPresent()) && !type.numeric()) {
      throw new IllegalArgumentException("bounds are only allowed on numeric fields");
    }
    if (min.isPresent() && max.isPresent() && min.get().compareTo(max.get()) > 0) {
      throw new IllegalArgumentException("min must not exceed max");
    }
    if (!allowedValues.isEmpty() && type != FieldType.TEXT && type != FieldType.IDENTIFIER) {
      throw new IllegalArgumentException("allowed values are only for TEXT and IDENTIFIER fields");
    }
    if (!nullable
        && (operators.contains(Operator.EXISTS) || operators.contains(Operator.NOT_EXISTS))) {
      throw new IllegalArgumentException("EXISTS/NOT_EXISTS require a nullable field");
    }
  }

  /** Convenience for open fields without bounds. */
  public static FieldDefinition of(String name, FieldType type, Set<Operator> operators) {
    return new FieldDefinition(
        name,
        type,
        operators,
        false,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Set.of());
  }
}

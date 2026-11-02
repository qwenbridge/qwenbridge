package io.qwenbridge.intelligence.ir;

import java.math.BigDecimal;
import java.util.Objects;

/** Explicit price constraint; no currency conversion or schema authorization is implied. */
public record QueryConstraint(
    String field, QueryOperator operator, BigDecimal value, String currency, int start, int end) {
  public QueryConstraint {
    Objects.requireNonNull(field, "field");
    Objects.requireNonNull(operator, "operator");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(currency, "currency");
    if (field.isBlank() || value.signum() < 0 || currency.isBlank() || start < 0 || end <= start) {
      throw new IllegalArgumentException("invalid query constraint");
    }
  }
}

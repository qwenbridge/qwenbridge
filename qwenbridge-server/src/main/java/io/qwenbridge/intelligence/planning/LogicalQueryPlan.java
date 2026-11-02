package io.qwenbridge.intelligence.planning;

import io.qwenbridge.intelligence.ir.QueryEntity;
import io.qwenbridge.intelligence.ir.QueryOperator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Validated logical plan only: no backend-specific DSL or authorization decision. */
public record LogicalQueryPlan(
    int irVersion,
    String originalQuery,
    List<QueryEntity> entities,
    List<PriceUpperBound> priceUpperBounds) {
  public LogicalQueryPlan {
    if (irVersion <= 0) throw new IllegalArgumentException("invalid IR version");
    Objects.requireNonNull(originalQuery, "originalQuery");
    if (originalQuery.isBlank()) throw new IllegalArgumentException("blank query");
    entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
    priceUpperBounds = List.copyOf(Objects.requireNonNull(priceUpperBounds, "priceUpperBounds"));
  }

  public record PriceUpperBound(
      String field, QueryOperator operator, BigDecimal amount, String currency) {
    public PriceUpperBound {
      Objects.requireNonNull(field, "field");
      Objects.requireNonNull(operator, "operator");
      Objects.requireNonNull(amount, "amount");
      Objects.requireNonNull(currency, "currency");
      if (field.isBlank()
          || currency.isBlank()
          || amount.signum() < 0
          || (operator != QueryOperator.LESS_THAN
              && operator != QueryOperator.LESS_THAN_OR_EQUAL)) {
        throw new IllegalArgumentException("invalid upper bound");
      }
    }
  }
}

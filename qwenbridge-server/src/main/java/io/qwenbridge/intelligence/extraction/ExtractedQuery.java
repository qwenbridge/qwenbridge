package io.qwenbridge.intelligence.extraction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Source-grounded extraction result. Does not imply schema validation or currency conversion. */
public record ExtractedQuery(
    List<GroundedEntity> entities, List<PriceConstraint> priceConstraints) {
  public ExtractedQuery {
    entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
    priceConstraints = List.copyOf(Objects.requireNonNull(priceConstraints, "priceConstraints"));
  }

  public record GroundedEntity(String value, String type, int start, int end) {
    public GroundedEntity {
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(type, "type");
      if (value.isBlank() || type.isBlank() || start < 0 || end <= start) {
        throw new IllegalArgumentException("invalid grounded entity");
      }
    }
  }

  public record PriceConstraint(
      Operator operator, BigDecimal amount, String currency, int start, int end) {
    public PriceConstraint {
      Objects.requireNonNull(operator, "operator");
      Objects.requireNonNull(amount, "amount");
      Objects.requireNonNull(currency, "currency");
      if (amount.signum() < 0 || currency.isBlank() || start < 0 || end <= start) {
        throw new IllegalArgumentException("invalid price constraint");
      }
    }
  }

  public enum Operator {
    LESS_THAN,
    LESS_THAN_OR_EQUAL
  }
}

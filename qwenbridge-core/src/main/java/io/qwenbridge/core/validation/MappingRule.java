package io.qwenbridge.core.validation;

import io.qwenbridge.core.queryir.Constraint.Operator;
import io.qwenbridge.core.queryir.IrValue;
import io.qwenbridge.core.queryir.Names;
import io.qwenbridge.core.queryir.SemanticSignal;
import java.util.Objects;

/**
 * An approved, typed rule that maps understood meaning onto schema fields (QB-052, QB-053). Rules
 * come from the owning Domain Pack; a model can never introduce one. Every application is recorded
 * in the resulting constraint's reason.
 */
public sealed interface MappingRule {

  String id();

  /** A field name used in text or proposed by extraction that means a schema field. */
  record FieldAlias(String id, String from, String to) implements MappingRule {
    public FieldAlias {
      Objects.requireNonNull(id, "id must not be null");
      if (!Names.valid(from) || !Names.valid(to)) {
        throw new IllegalArgumentException("alias names must be valid names");
      }
    }
  }

  /**
   * For schemas that store money as a plain number field plus a currency text field: a money
   * constraint becomes a constraint on {@code numberField} and, when the currency is known, an
   * equality on {@code currencyField}. An unknown currency is never filled in.
   */
  record MoneyToNumberAndCurrency(String id, String numberField, String currencyField)
      implements MappingRule {
    public MoneyToNumberAndCurrency {
      Objects.requireNonNull(id, "id must not be null");
      if (!Names.valid(numberField) || !Names.valid(currencyField)) {
        throw new IllegalArgumentException("field names must be valid names");
      }
    }
  }

  /**
   * An approved interpretation of a subjective concept as a structured preference, e.g.
   * "lightweight" as {@code weight LTE 1 kg}. The result is always SOFT and INFERRED: it can rank,
   * never exclude.
   */
  record SignalToSoftConstraint(
      String id,
      String concept,
      SemanticSignal.Polarity polarity,
      String field,
      Operator operator,
      IrValue value)
      implements MappingRule {
    public SignalToSoftConstraint {
      Objects.requireNonNull(id, "id must not be null");
      Objects.requireNonNull(polarity, "polarity must not be null");
      Objects.requireNonNull(operator, "operator must not be null");
      Objects.requireNonNull(value, "value must not be null");
      if (!Names.valid(concept) || !Names.valid(field)) {
        throw new IllegalArgumentException("concept and field must be valid names");
      }
    }
  }
}

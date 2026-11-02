package io.qwenbridge.core.queryir;

import io.qwenbridge.core.confidence.Confidence;
import java.util.Objects;
import java.util.Optional;

/**
 * A requirement on a field (QB-030–QB-033, QB-038). Negation is part of the operator (NE, NOT_IN,
 * NOT_CONTAINS, NOT_EXISTS) so an exclusion can never be confused with a positive preference.
 *
 * @param field schema field path, e.g. {@code price}, {@code memory_usage}, {@code bot_score}
 * @param value absent only for EXISTS and NOT_EXISTS
 * @param strength HARD must be met; SOFT is a preference that may be relaxed with consent
 * @param provenance where the constraint came from; USER_TEXT requires a span
 * @param reason why it was classified with this strength (never empty)
 * @param scope subquery id this applies to; empty means the whole query
 */
public record Constraint(
    String id,
    String field,
    Operator operator,
    Optional<IrValue> value,
    Strength strength,
    Provenance provenance,
    String reason,
    Optional<Span> span,
    Confidence confidence,
    Optional<String> scope) {

  public enum Operator {
    EQ,
    NE,
    LT,
    LTE,
    GT,
    GTE,
    BETWEEN,
    IN,
    NOT_IN,
    CONTAINS,
    NOT_CONTAINS,
    EXISTS,
    NOT_EXISTS;

    public boolean negative() {
      return this == NE || this == NOT_IN || this == NOT_CONTAINS || this == NOT_EXISTS;
    }

    public boolean ordering() {
      return this == LT || this == LTE || this == GT || this == GTE;
    }

    public boolean valueless() {
      return this == EXISTS || this == NOT_EXISTS;
    }
  }

  public enum Strength {
    HARD,
    SOFT
  }

  public enum Provenance {
    /** Stated by the user in the query text. */
    USER_TEXT,
    /** Carried from validated conversation or request context. */
    CONTEXT,
    /** Inferred from semantics; must be marked SOFT unless a domain rule makes it hard. */
    INFERRED,
    /** Added by an approved domain or policy rule. */
    DOMAIN_RULE
  }

  public Constraint {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(field, "field must not be null");
    Objects.requireNonNull(operator, "operator must not be null");
    value = value == null ? Optional.empty() : value;
    Objects.requireNonNull(strength, "strength must not be null");
    Objects.requireNonNull(provenance, "provenance must not be null");
    Objects.requireNonNull(reason, "reason must not be null");
    span = span == null ? Optional.empty() : span;
    Objects.requireNonNull(confidence, "confidence must not be null");
    scope = scope == null ? Optional.empty() : scope;
  }

  public boolean negative() {
    return operator.negative();
  }
}

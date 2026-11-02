package io.qwenbridge.core.recovery;

import io.qwenbridge.core.queryir.Constraint;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One proposed relaxation: a single change to a single constraint (QB-128).
 *
 * @param after the relaxed constraint, or empty when the constraint is removed
 * @param alternative the complete constraint list of the relaxed query; every other constraint is
 *     unchanged
 * @param requiresConsent true for a HARD constraint: it is offered, never applied on its own
 */
public record Relaxation(
    String constraintId,
    String field,
    RelaxationRule.Kind kind,
    Constraint before,
    Optional<Constraint> after,
    List<Constraint> alternative,
    boolean requiresConsent) {
  public Relaxation {
    Objects.requireNonNull(constraintId, "constraintId must not be null");
    Objects.requireNonNull(before, "before must not be null");
    after = after == null ? Optional.empty() : after;
    alternative = List.copyOf(alternative);
  }
}

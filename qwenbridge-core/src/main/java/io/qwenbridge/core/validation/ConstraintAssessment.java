package io.qwenbridge.core.validation;

import io.qwenbridge.core.schema.SchemaViolation;
import java.util.List;
import java.util.Objects;

/**
 * Outcome for one constraint (QB-050, QB-051). Nothing is silently dropped: every constraint of the
 * input appears with a status and, when not executable, the reasons.
 */
public record ConstraintAssessment(
    String constraintId, Status status, List<SchemaViolation> violations, String note) {

  public enum Status {
    /** Schema-valid; may be compiled (HARD as a filter, SOFT as a preference). */
    EXECUTABLE,
    /** Schema-valid SOFT preference from an uncertain source; usable for ranking only. */
    UNCERTAIN_SOFT,
    /** SOFT preference the schema cannot express; reported and not compiled. */
    UNSUPPORTED_SOFT,
    /** HARD requirement the schema cannot express; blocks execution until clarified. */
    INVALID_HARD,
    /** HARD requirement that conflicts with another HARD requirement; blocks execution. */
    CONTRADICTORY
  }

  public ConstraintAssessment {
    Objects.requireNonNull(constraintId, "constraintId must not be null");
    Objects.requireNonNull(status, "status must not be null");
    violations = violations == null ? List.of() : List.copyOf(violations);
    Objects.requireNonNull(note, "note must not be null");
  }

  public boolean blocking() {
    return status == Status.INVALID_HARD || status == Status.CONTRADICTORY;
  }
}

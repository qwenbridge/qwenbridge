package io.qwenbridge.core.validation;

import java.util.List;
import java.util.Optional;

/**
 * Result of constraint validation (QB-050–QB-055).
 *
 * @param validated present only when nothing blocks execution
 * @param assessments one entry per constraint of the mapped QueryIR
 * @param lostNegations ids of input exclusions that did not survive as exclusions (always empty
 *     unless a transformation is defective; non-empty blocks execution)
 */
public record ValidationReport(
    Optional<ValidatedQuery> validated,
    List<ConstraintAssessment> assessments,
    List<String> lostNegations) {

  public ValidationReport {
    validated = validated == null ? Optional.empty() : validated;
    assessments = assessments == null ? List.of() : List.copyOf(assessments);
    lostNegations = lostNegations == null ? List.of() : List.copyOf(lostNegations);
  }

  public boolean blocking() {
    return validated.isEmpty();
  }

  public List<ConstraintAssessment> blockingAssessments() {
    return assessments.stream().filter(ConstraintAssessment::blocking).toList();
  }
}

package io.qwenbridge.model;

import io.qwenbridge.core.validation.ConstraintAssessment;
import io.qwenbridge.pipeline.result.ConstraintValidationResult;
import java.util.List;

/**
 * Public view of the constraint validation gate (QB-050–QB-056). Lists every constraint with its
 * status; violations carry a path and a code, never user values.
 *
 * @param status VALIDATED, BLOCKED, NO_SCHEMA or NO_QUERY_IR
 * @param schema the schema key, e.g. {@code commerce@1.0}, when one applied
 * @param enforced always false in v1: execution does not yet consume the validated query
 */
public record ConstraintValidationResponse(
    String status,
    String schema,
    boolean enforced,
    List<String> lostNegations,
    List<Assessment> assessments) {

  public ConstraintValidationResponse {
    lostNegations = lostNegations == null ? List.of() : List.copyOf(lostNegations);
    assessments = assessments == null ? List.of() : List.copyOf(assessments);
  }

  public record Assessment(
      String constraintId,
      String status,
      boolean blocking,
      String note,
      List<Violation> violations) {
    public Assessment {
      violations = violations == null ? List.of() : List.copyOf(violations);
    }

    static Assessment from(ConstraintAssessment a) {
      return new Assessment(
          a.constraintId(),
          a.status().name(),
          a.blocking(),
          a.note(),
          a.violations().stream().map(v -> new Violation(v.path(), v.code().name())).toList());
    }
  }

  public record Violation(String path, String code) {}

  public static ConstraintValidationResponse from(ConstraintValidationResult result) {
    if (result == null) {
      return null;
    }
    return new ConstraintValidationResponse(
        result.status().name(),
        result.schema().map(Object::toString).orElse(null),
        false,
        result.report().map(r -> r.lostNegations()).orElse(List.of()),
        result
            .report()
            .map(r -> r.assessments().stream().map(Assessment::from).toList())
            .orElse(List.of()));
  }
}

package io.qwenbridge.pipeline.result;

import io.qwenbridge.core.schema.SchemaKey;
import io.qwenbridge.core.validation.ValidationReport;
import java.util.Objects;
import java.util.Optional;

/** Outcome of the Core constraint validation gate for one request (QB-050–QB-056). */
public record ConstraintValidationResult(
    Status status, Optional<SchemaKey> schema, Optional<ValidationReport> report) {

  public enum Status {
    /** Every constraint is executable or a reported non-blocking preference. */
    VALIDATED,
    /** At least one HARD constraint is invalid or contradictory, or a negation was lost. */
    BLOCKED,
    /** No domain schema applies; constraints stay proposals and nothing may be compiled. */
    NO_SCHEMA,
    /** No valid QueryIR was assembled. */
    NO_QUERY_IR
  }

  public ConstraintValidationResult {
    Objects.requireNonNull(status, "status must not be null");
    schema = schema == null ? Optional.empty() : schema;
    report = report == null ? Optional.empty() : report;
  }

  public static ConstraintValidationResult of(SchemaKey schema, ValidationReport report) {
    return new ConstraintValidationResult(
        report.blocking() ? Status.BLOCKED : Status.VALIDATED,
        Optional.of(schema),
        Optional.of(report));
  }

  public static ConstraintValidationResult without(Status status) {
    return new ConstraintValidationResult(status, Optional.empty(), Optional.empty());
  }
}

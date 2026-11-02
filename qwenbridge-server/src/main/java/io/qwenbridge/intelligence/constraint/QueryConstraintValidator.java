package io.qwenbridge.intelligence.constraint;

import io.qwenbridge.intelligence.ir.QueryConstraint;
import io.qwenbridge.intelligence.ir.QueryIR;
import io.qwenbridge.intelligence.schema.QuerySchemaValidator;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Validates logical constraints after schema validation, without executing a query. */
@ApplicationScoped
public class QueryConstraintValidator {
  private final QuerySchemaValidator schemaValidator;

  public QueryConstraintValidator(QuerySchemaValidator schemaValidator) {
    this.schemaValidator = Objects.requireNonNull(schemaValidator, "schemaValidator");
  }

  public Result validate(QueryIR query) {
    Objects.requireNonNull(query, "query");
    QuerySchemaValidator.ValidationResult schema = schemaValidator.validate(query);
    List<String> errors = new ArrayList<>(schema.errors());
    Set<ConstraintKey> seen = new HashSet<>();
    for (QueryConstraint constraint : query.constraints()) {
      ConstraintKey key =
          new ConstraintKey(
              constraint.field(),
              constraint.operator().name(),
              constraint.value().stripTrailingZeros(),
              constraint.currency());
      if (!seen.add(key)) {
        errors.add("duplicate constraint: " + constraint.field() + " " + constraint.currency());
      }
    }
    // Multiple upper bounds are compatible: the strictest one wins at planning time.
    // Different currencies must never be compared without an explicit conversion policy.
    return new Result(errors.isEmpty(), errors);
  }

  private record ConstraintKey(String field, String operator, BigDecimal amount, String currency) {}

  public record Result(boolean valid, List<String> errors) {
    public Result {
      errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
      if (valid != errors.isEmpty()) {
        throw new IllegalArgumentException("valid flag must agree with errors");
      }
    }
  }
}

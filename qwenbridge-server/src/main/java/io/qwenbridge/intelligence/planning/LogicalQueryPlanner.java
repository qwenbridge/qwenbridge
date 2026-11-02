package io.qwenbridge.intelligence.planning;

import io.qwenbridge.intelligence.constraint.QueryConstraintValidator;
import io.qwenbridge.intelligence.ir.QueryConstraint;
import io.qwenbridge.intelligence.ir.QueryIR;
import io.qwenbridge.intelligence.ir.QueryOperator;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Stage 9: converts validated QueryIR into a deterministic, backend-neutral plan. */
@ApplicationScoped
public class LogicalQueryPlanner {
  private final QueryConstraintValidator validator;

  public LogicalQueryPlanner(QueryConstraintValidator validator) {
    this.validator = Objects.requireNonNull(validator, "validator");
  }

  public LogicalQueryPlan plan(QueryIR ir) {
    Objects.requireNonNull(ir, "ir");
    var validation = validator.validate(ir);
    if (!validation.valid()) throw new QueryPlanningException(validation.errors());

    // Tighten upper bounds only when the field AND currency match.
    // Strict (<) wins over inclusive (<=) when amounts are equal.
    Map<FieldCurrency, LogicalQueryPlan.PriceUpperBound> tightest = new LinkedHashMap<>();
    for (QueryConstraint constraint : ir.constraints()) {
      var key = new FieldCurrency(constraint.field(), constraint.currency());
      var candidate =
          new LogicalQueryPlan.PriceUpperBound(
              constraint.field(), constraint.operator(), constraint.value(), constraint.currency());
      var existing = tightest.get(key);
      if (existing == null
          || candidate.amount().compareTo(existing.amount()) < 0
          || (candidate.amount().compareTo(existing.amount()) == 0
              && candidate.operator() == QueryOperator.LESS_THAN
              && existing.operator() == QueryOperator.LESS_THAN_OR_EQUAL)) {
        tightest.put(key, candidate);
      }
    }
    return new LogicalQueryPlan(
        ir.version(), ir.originalQuery(), ir.entities(), new ArrayList<>(tightest.values()));
  }

  private record FieldCurrency(String field, String currency) {}
}

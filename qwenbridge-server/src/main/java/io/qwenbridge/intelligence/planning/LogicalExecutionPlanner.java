package io.qwenbridge.intelligence.planning;

import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchDecision;
import io.qwenbridge.execution.ExecutionOperation;
import io.qwenbridge.execution.ExecutionPlan;
import io.qwenbridge.execution.ExecutionPlanFactory;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Objects;

/**
 * Stage 10: safe bridge from logical planning to the existing execution decision contract.
 *
 * <p>Structured predicates are intentionally rejected until a search provider can enforce them.
 * Silently dropping a price constraint would return results outside the requested bounds.
 */
@ApplicationScoped
public class LogicalExecutionPlanner {
  private final ExecutionPlanFactory factory;

  public LogicalExecutionPlanner(ExecutionPlanFactory factory) {
    this.factory = Objects.requireNonNull(factory, "factory");
  }

  public ExecutionPlan plan(LogicalQueryPlan logical, SearchDecision decision) {
    Objects.requireNonNull(logical, "logical");
    Objects.requireNonNull(decision, "decision");
    if (!logical.priceUpperBounds().isEmpty()) {
      throw new UnsupportedOperationException(
          "Structured price filters are not supported by the current execution providers");
    }
    // Entity annotations are evidence, not filters; keep the original search decision.
    ExecutionPlan execution = factory.from(decision);
    if (decision.backend() == SearchBackend.NONE
        && (execution.contains(ExecutionOperation.KEYWORD_SEARCH)
            || execution.contains(ExecutionOperation.VECTOR_SEARCH)
            || execution.contains(ExecutionOperation.HYBRID_SEARCH))) {
      throw new IllegalArgumentException("Search operations require a search backend");
    }
    return execution;
  }
}

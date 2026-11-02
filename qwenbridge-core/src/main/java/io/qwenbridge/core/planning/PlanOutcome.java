package io.qwenbridge.core.planning;

import java.util.List;
import java.util.Objects;

/** The planner's typed answer (QB-104). */
public sealed interface PlanOutcome {

  record Planned(RetrievalPlan plan) implements PlanOutcome {
    public Planned {
      Objects.requireNonNull(plan, "plan must not be null");
    }
  }

  /** Nothing available can serve the query as validated; the requirements are named. */
  record Unsupported(List<String> requirements, List<PlanReason> reasons) implements PlanOutcome {
    public Unsupported {
      requirements = List.copyOf(requirements);
      reasons = List.copyOf(reasons);
    }
  }
}

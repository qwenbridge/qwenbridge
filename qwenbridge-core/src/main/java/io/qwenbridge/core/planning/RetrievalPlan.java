package io.qwenbridge.core.planning;

import io.qwenbridge.core.queryir.Constraint;
import io.qwenbridge.core.retrieval.filter.Filter;
import io.qwenbridge.core.retrieval.hybrid.HybridResult.Strategy;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A typed, versioned, backend-neutral retrieval plan (QB-099). It replaces the three earlier plan
 * shapes (v1 ExecutionPlan, Commerce Plan, search-mode flags) as the planner's output.
 *
 * @param text the text used for keyword and embedding (the query without its compiled constraints)
 * @param filter HARD constraints, compiled; every result must satisfy it (re-checked after
 *     retrieval)
 * @param preferences SOFT constraints; ranking inputs, never filters
 * @param stages what runs, in order
 * @param fallback what may run instead if the strategy cannot (always recorded when used)
 * @param reasons why this plan was chosen, as stable codes
 */
public record RetrievalPlan(
    String version,
    String tenant,
    Strategy strategy,
    String text,
    Filter filter,
    List<Constraint> preferences,
    int topK,
    Budget budget,
    List<Stage> stages,
    Optional<Strategy> fallback,
    List<PlanReason> reasons) {

  public static final String VERSION = "1.0";

  /** One step of a plan. */
  public record Stage(Kind kind, Optional<String> provider, int candidates, Duration timeout) {
    public enum Kind {
      EMBED_QUERY,
      KEYWORD_RETRIEVAL,
      VECTOR_RETRIEVAL,
      FUSE,
      VALIDATE_HARD_CONSTRAINTS
    }

    public Stage {
      Objects.requireNonNull(kind, "kind must not be null");
      provider = provider == null ? Optional.empty() : provider;
      Objects.requireNonNull(timeout, "timeout must not be null");
    }
  }

  /** Limits the plan must respect (QB-105). */
  public record Budget(Duration timeout, int maxProviderCalls) {
    public Budget {
      Objects.requireNonNull(timeout, "timeout must not be null");
      if (maxProviderCalls < 1) {
        throw new IllegalArgumentException("at least one provider call");
      }
    }
  }

  public RetrievalPlan {
    if (!VERSION.equals(version)) {
      throw new IllegalArgumentException("unsupported plan version");
    }
    Objects.requireNonNull(tenant, "tenant must not be null");
    Objects.requireNonNull(strategy, "strategy must not be null");
    Objects.requireNonNull(text, "text must not be null");
    Objects.requireNonNull(filter, "filter must not be null");
    preferences = List.copyOf(preferences);
    Objects.requireNonNull(budget, "budget must not be null");
    stages = List.copyOf(stages);
    fallback = fallback == null ? Optional.empty() : fallback;
    reasons = List.copyOf(reasons);
    if (topK < 1) {
      throw new IllegalArgumentException("topK must be positive");
    }
    if (stages.stream().noneMatch(s -> s.kind() == Stage.Kind.VALIDATE_HARD_CONSTRAINTS)) {
      throw new IllegalArgumentException("every plan re-validates HARD constraints");
    }
    if (reasons.isEmpty()) {
      throw new IllegalArgumentException("a plan must say why it was chosen");
    }
  }
}

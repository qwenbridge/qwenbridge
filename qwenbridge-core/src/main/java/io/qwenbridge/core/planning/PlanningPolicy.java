package io.qwenbridge.core.planning;

import java.time.Duration;
import java.util.Objects;

/**
 * Cost, latency and quality preferences for planning (QB-105).
 *
 * @param objective what to favour when several strategies are possible
 * @param timeout total time budget for retrieval
 * @param maxProviderCalls at most this many provider calls (hybrid needs two)
 */
public record PlanningPolicy(Objective objective, Duration timeout, int maxProviderCalls) {

  public enum Objective {
    /** Fewest calls: one provider, keyword unless only meaning can help. */
    LATENCY,
    /** Hybrid when lexical and semantic needs both apply; otherwise the matching single mode. */
    BALANCED,
    /** Hybrid whenever both sides are available. */
    QUALITY
  }

  public static final PlanningPolicy DEFAULT =
      new PlanningPolicy(Objective.BALANCED, Duration.ofSeconds(5), 2);

  public PlanningPolicy {
    Objects.requireNonNull(objective, "objective must not be null");
    Objects.requireNonNull(timeout, "timeout must not be null");
    if (maxProviderCalls < 1) {
      throw new IllegalArgumentException("maxProviderCalls must be positive");
    }
  }
}

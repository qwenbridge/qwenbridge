package io.qwenbridge.core.planning;

import io.qwenbridge.core.retrieval.SearchProvider;
import java.time.Duration;
import java.util.Objects;

/**
 * A provider the planner may use, with its expected latency (QB-103, QB-105). The latency is an
 * operator-configured estimate used for budgeting, not a measurement claim.
 */
public record CandidateProvider(SearchProvider provider, Duration expectedLatency) {

  public CandidateProvider {
    Objects.requireNonNull(provider, "provider must not be null");
    Objects.requireNonNull(expectedLatency, "expectedLatency must not be null");
  }
}

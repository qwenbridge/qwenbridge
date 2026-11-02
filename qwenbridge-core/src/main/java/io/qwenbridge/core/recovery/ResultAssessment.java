package io.qwenbridge.core.recovery;

import java.util.Objects;

/**
 * What an executed query returned (QB-125). Zero results from a working provider and zero results
 * because the provider failed are different: only the first can be helped by relaxing constraints;
 * relaxing after a failure would broaden the query for nothing and hide the failure.
 *
 * @param lowThreshold below this many results (and above zero) the outcome is LOW_RESULTS; 0
 *     disables low-result detection
 */
public record ResultAssessment(Outcome outcome, int found, int lowThreshold) {

  public enum Outcome {
    RESULTS,
    LOW_RESULTS,
    ZERO_RESULTS,
    /** A provider failed or could not serve the query; the count says nothing about the data. */
    PROVIDER_FAILED,
    /** Retrieval did not run (blocked, clarification required, unsupported). */
    NOT_EXECUTED
  }

  public ResultAssessment {
    Objects.requireNonNull(outcome, "outcome must not be null");
    if (found < 0 || lowThreshold < 0) {
      throw new IllegalArgumentException("counts are non-negative");
    }
  }

  public static ResultAssessment of(
      boolean executed, boolean providerFailed, int found, int lowThreshold) {
    Outcome outcome;
    if (!executed) {
      outcome = Outcome.NOT_EXECUTED;
    } else if (providerFailed && found == 0) {
      outcome = Outcome.PROVIDER_FAILED;
    } else if (found == 0) {
      outcome = Outcome.ZERO_RESULTS;
    } else if (found < lowThreshold) {
      outcome = Outcome.LOW_RESULTS;
    } else {
      outcome = Outcome.RESULTS;
    }
    return new ResultAssessment(outcome, found, lowThreshold);
  }

  /** Whether relaxing constraints can help. */
  public boolean recoverable() {
    return outcome == Outcome.ZERO_RESULTS || outcome == Outcome.LOW_RESULTS;
  }
}

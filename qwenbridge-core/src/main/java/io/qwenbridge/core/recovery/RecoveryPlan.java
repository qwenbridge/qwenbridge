package io.qwenbridge.core.recovery;

import java.util.List;
import java.util.Objects;

/**
 * The recovery decision for one query (QB-130): the assessment, the alternatives offered and, for
 * every constraint that was not relaxed, why.
 */
public record RecoveryPlan(
    String policy,
    ResultAssessment assessment,
    List<Relaxation> proposals,
    List<Skipped> skipped,
    List<RecoveryReason> reasons) {

  public record Skipped(String constraintId, String field, RecoveryReason reason) {
    public Skipped {
      Objects.requireNonNull(constraintId, "constraintId must not be null");
      Objects.requireNonNull(reason, "reason must not be null");
    }
  }

  public RecoveryPlan {
    Objects.requireNonNull(policy, "policy must not be null");
    Objects.requireNonNull(assessment, "assessment must not be null");
    proposals = List.copyOf(proposals);
    skipped = List.copyOf(skipped);
    reasons = List.copyOf(reasons);
  }
}

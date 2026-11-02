package io.qwenbridge.core.policy;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The answer to a {@link PolicyRequest} (QB-071).
 *
 * @param reasons why, as stable codes (never free text with user data)
 * @param policyIds the policy rules that contributed
 * @param approval the approval this request needs, when it is high-risk or a rule asked for one
 * @param approvalsEnforced whether a missing approval blocks; when false the decision reports it
 */
public record PolicyDecision(
    Outcome outcome,
    OperationSpec.Effect effect,
    List<ReasonCode> reasons,
    List<String> policyIds,
    Optional<ApprovalRequirement> approval,
    boolean approvalsEnforced) {

  public enum Outcome {
    ALLOW,
    DENY,
    REQUIRES_APPROVAL
  }

  public PolicyDecision {
    Objects.requireNonNull(outcome, "outcome must not be null");
    reasons = List.copyOf(reasons);
    policyIds = List.copyOf(policyIds);
    approval = approval == null ? Optional.empty() : approval;
    if (reasons.isEmpty()) {
      throw new IllegalArgumentException("every decision needs a reason");
    }
  }

  public boolean allowed() {
    return outcome == Outcome.ALLOW;
  }
}

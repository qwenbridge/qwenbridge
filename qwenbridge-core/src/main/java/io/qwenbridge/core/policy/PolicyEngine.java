package io.qwenbridge.core.policy;

import io.qwenbridge.core.policy.PolicyDecision.Outcome;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The central authorization point (QB-071–QB-076). Every path that executes on behalf of a caller
 * (endpoints, connectors, plugins, agents) asks this engine; nothing else may grant access.
 *
 * <ol>
 *   <li>Unknown operations are denied (default deny).
 *   <li>SENSITIVE and HIGH_RISK operations need an authenticated subject.
 *   <li>The resource must be in the subject's tenant.
 *   <li>The subject must hold a scope covering the operation and resource.
 *   <li>Policy rules may then deny or ask for approval; a failing rule denies. Rules never grant.
 *   <li>HIGH_RISK operations, and requests a rule flagged, need a valid approval: same operation,
 *       resource and tenant, current, given by someone else, not longer-lived than allowed. When
 *       approvals are not enforced the request is allowed with {@link
 *       ReasonCode#APPROVAL_NOT_ENFORCED} and the requirement is reported.
 * </ol>
 */
public final class PolicyEngine {

  public static final String APPROVER_SCOPE = "approvals:grant";

  private final OperationCatalog catalog;
  private final Supplier<List<PolicyRule>> rules;
  private final Clock clock;
  private final boolean approvalsEnforced;
  private final Duration maxApprovalValidity;

  public PolicyEngine(
      OperationCatalog catalog,
      Supplier<List<PolicyRule>> rules,
      Clock clock,
      boolean approvalsEnforced,
      Duration maxApprovalValidity) {
    this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
    this.rules = Objects.requireNonNull(rules, "rules must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
    this.approvalsEnforced = approvalsEnforced;
    this.maxApprovalValidity =
        Objects.requireNonNull(maxApprovalValidity, "maxApprovalValidity must not be null");
  }

  public PolicyDecision decide(PolicyRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    Optional<OperationSpec> found = catalog.find(request.operation());
    if (found.isEmpty()) {
      return deny(null, ReasonCode.UNKNOWN_OPERATION, List.of());
    }
    OperationSpec spec = found.get();
    Subject subject = request.subject();
    Resource resource = request.resource();
    if (!subject.authenticated() && spec.sensitivity() != OperationSpec.Sensitivity.NORMAL) {
      return deny(spec, ReasonCode.UNAUTHENTICATED, List.of());
    }
    if (!subject.tenant().equals(resource.tenant())) {
      return deny(spec, ReasonCode.TENANT_MISMATCH, List.of());
    }
    if (subject.scopes().stream().noneMatch(s -> s.covers(request.operation(), resource))) {
      return deny(spec, ReasonCode.NO_SCOPE, List.of());
    }

    List<String> policyIds = new ArrayList<>();
    boolean approvalRequested = spec.sensitivity() == OperationSpec.Sensitivity.HIGH_RISK;
    List<ReasonCode> reasons = new ArrayList<>(List.of(ReasonCode.ALLOWED_BY_SCOPE));
    for (PolicyRule rule : rules.get()) {
      PolicyRule.Verdict verdict;
      try {
        verdict = rule.evaluate(request);
      } catch (RuntimeException e) {
        return deny(spec, ReasonCode.POLICY_ERROR, List.of(rule.id()));
      }
      switch (verdict) {
        case DENY -> {
          return deny(spec, ReasonCode.POLICY_DENY, List.of(rule.id()));
        }
        case REQUIRE_APPROVAL -> {
          policyIds.add(rule.id());
          reasons.add(ReasonCode.POLICY_REVIEW);
          approvalRequested = true;
        }
        case NOT_APPLICABLE -> {}
      }
    }
    if (!approvalRequested) {
      return new PolicyDecision(
          Outcome.ALLOW, spec.effect(), reasons, policyIds, Optional.empty(), approvalsEnforced);
    }

    ApprovalRequirement requirement =
        new ApprovalRequirement(request.operation(), resource, APPROVER_SCOPE, maxApprovalValidity);
    Optional<ReasonCode> problem = check(request, request.approval());
    if (problem.isEmpty()) {
      reasons.add(ReasonCode.APPROVED);
      return new PolicyDecision(
          Outcome.ALLOW,
          spec.effect(),
          reasons,
          policyIds,
          Optional.of(requirement),
          approvalsEnforced);
    }
    if (!approvalsEnforced) {
      reasons.add(ReasonCode.APPROVAL_NOT_ENFORCED);
      return new PolicyDecision(
          Outcome.ALLOW, spec.effect(), reasons, policyIds, Optional.of(requirement), false);
    }
    reasons.add(problem.get());
    return new PolicyDecision(
        Outcome.REQUIRES_APPROVAL,
        spec.effect(),
        reasons,
        policyIds,
        Optional.of(requirement),
        true);
  }

  private Optional<ReasonCode> check(PolicyRequest request, Optional<Approval> given) {
    if (given.isEmpty()) {
      return Optional.of(ReasonCode.APPROVAL_REQUIRED);
    }
    Approval approval = given.get();
    if (!approval.operation().equals(request.operation())
        || !approval.resource().equals(request.resource())) {
      return Optional.of(ReasonCode.APPROVAL_MISMATCH);
    }
    if (approval.approver().equals(request.subject().id())) {
      return Optional.of(ReasonCode.APPROVAL_SELF);
    }
    if (approval.validity().compareTo(maxApprovalValidity) > 0) {
      return Optional.of(ReasonCode.APPROVAL_TOO_LONG);
    }
    Instant now = clock.instant();
    if (now.isBefore(approval.issuedAt()) || !now.isBefore(approval.expiresAt())) {
      return Optional.of(ReasonCode.APPROVAL_EXPIRED);
    }
    return Optional.empty();
  }

  private PolicyDecision deny(OperationSpec spec, ReasonCode reason, List<String> policyIds) {
    return new PolicyDecision(
        Outcome.DENY,
        spec == null ? null : spec.effect(),
        List.of(reason),
        policyIds,
        Optional.empty(),
        approvalsEnforced);
  }
}

package io.qwenbridge.security;

import io.qwenbridge.core.policy.Approval;
import io.qwenbridge.core.policy.PolicyEngine;
import io.qwenbridge.core.policy.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues and redeems approvals for high-risk operations (QB-075). An approval is bound to one
 * operation on one resource in the approver's tenant, expires, and is single-use. Only a principal
 * holding {@value PolicyEngine#APPROVER_SCOPE} may issue one; the engine rejects it when the
 * approver is the requester. Approvals are kept in memory: they are short-lived by design and do
 * not survive a restart.
 */
@ApplicationScoped
public class ApprovalStore {

  private final Map<String, Approval> approvals = new ConcurrentHashMap<>();
  private final Clock clock = Clock.systemUTC();

  public Approval issue(
      ApiIdentity.Principal approver,
      String operation,
      String resourceType,
      Optional<String> resourceId,
      Duration validity) {
    Resource resource = Resource.of(resourceType, approver.tenant()).named(resourceId.orElse(null));
    Authorization.require(
        approver,
        PolicyEngine.APPROVER_SCOPE,
        Authorization.resourceFor(PolicyEngine.APPROVER_SCOPE, approver.tenant()));
    if (Authorization.CATALOG.find(operation).isEmpty()) {
      throw new IllegalArgumentException("Unknown operation");
    }
    Instant now = clock.instant();
    Approval approval =
        new Approval(
            UUID.randomUUID().toString(),
            approver.subject(),
            operation,
            resource,
            now,
            now.plus(validity));
    approvals.put(approval.id(), approval);
    return approval;
  }

  /** The approval with this id, if it exists and has not been used. */
  public Optional<Approval> find(String id) {
    return Optional.ofNullable(approvals.get(id));
  }

  /** Marks an approval as used; it cannot authorize a second request. */
  public void consume(String id) {
    approvals.remove(id);
  }
}

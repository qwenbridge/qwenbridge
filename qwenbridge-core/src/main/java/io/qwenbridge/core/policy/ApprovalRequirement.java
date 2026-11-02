package io.qwenbridge.core.policy;

import java.time.Duration;
import java.util.Objects;

/**
 * What approval a request needs (QB-075): this operation on this resource, by someone other than
 * the requester who holds {@code approverScope}, valid for at most {@code maxValidity}.
 */
public record ApprovalRequirement(
    String operation, Resource resource, String approverScope, Duration maxValidity) {

  public ApprovalRequirement {
    Objects.requireNonNull(operation, "operation must not be null");
    Objects.requireNonNull(resource, "resource must not be null");
    Objects.requireNonNull(approverScope, "approverScope must not be null");
    Objects.requireNonNull(maxValidity, "maxValidity must not be null");
  }
}

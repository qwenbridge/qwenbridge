package io.qwenbridge.core.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A recorded approval of one high-risk operation on one resource (QB-075). It is issued by a
 * trusted approval service, which has already verified that the approver holds the approval scope;
 * the engine checks that it fits the request exactly, is current, and was not given by the
 * requester.
 */
public record Approval(
    String id,
    String approver,
    String operation,
    Resource resource,
    Instant issuedAt,
    Instant expiresAt) {

  public Approval {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(approver, "approver must not be null");
    Objects.requireNonNull(operation, "operation must not be null");
    Objects.requireNonNull(resource, "resource must not be null");
    Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    if (!expiresAt.isAfter(issuedAt)) {
      throw new IllegalArgumentException("an approval must expire after it is issued");
    }
  }

  public Duration validity() {
    return Duration.between(issuedAt, expiresAt);
  }
}

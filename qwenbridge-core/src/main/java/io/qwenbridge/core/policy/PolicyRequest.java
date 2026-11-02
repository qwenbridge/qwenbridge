package io.qwenbridge.core.policy;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** One authorization question (QB-071). */
public record PolicyRequest(
    Subject subject,
    String operation,
    Resource resource,
    Map<String, String> context,
    Optional<Approval> approval) {

  public PolicyRequest {
    Objects.requireNonNull(subject, "subject must not be null");
    Objects.requireNonNull(operation, "operation must not be null");
    Objects.requireNonNull(resource, "resource must not be null");
    context = context == null ? Map.of() : Map.copyOf(context);
    approval = approval == null ? Optional.empty() : approval;
  }

  public static PolicyRequest of(Subject subject, String operation, Resource resource) {
    return new PolicyRequest(subject, operation, resource, Map.of(), Optional.empty());
  }
}

package io.qwenbridge.core.policy;

import java.util.Objects;
import java.util.Optional;

/**
 * A permission (QB-073): one operation, always within one tenant, optionally narrowed to a domain,
 * a connector or a resource type. An empty narrowing means "any". There are no wildcard operations
 * or tenants, so a scope can never reach across tenants.
 */
public record Scope(
    String operation,
    String tenant,
    Optional<String> domain,
    Optional<String> connector,
    Optional<String> resourceType) {

  public Scope {
    Objects.requireNonNull(operation, "operation must not be null");
    Objects.requireNonNull(tenant, "tenant must not be null");
    domain = domain == null ? Optional.empty() : domain;
    connector = connector == null ? Optional.empty() : connector;
    resourceType = resourceType == null ? Optional.empty() : resourceType;
    if (operation.isBlank() || operation.contains("*") || tenant.contains("*")) {
      throw new IllegalArgumentException("scopes name one operation in one tenant");
    }
  }

  public static Scope of(String operation, String tenant) {
    return new Scope(operation, tenant, Optional.empty(), Optional.empty(), Optional.empty());
  }

  public boolean covers(String operation, Resource resource) {
    return this.operation.equals(operation)
        && tenant.equals(resource.tenant())
        && narrows(domain, resource.domain())
        && narrows(connector, resource.connector())
        && resourceType.map(t -> t.equals(resource.type())).orElse(true);
  }

  private static boolean narrows(Optional<String> required, Optional<String> actual) {
    return required.isEmpty() || required.equals(actual);
  }
}

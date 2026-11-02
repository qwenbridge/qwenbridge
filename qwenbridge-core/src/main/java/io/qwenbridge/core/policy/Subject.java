package io.qwenbridge.core.policy;

import java.util.Objects;
import java.util.Set;

/**
 * Who is asking (QB-071). {@code authenticated} is false for the anonymous principal of deployments
 * that allow anonymous search; such a subject can only use NORMAL operations it holds a scope for.
 */
public record Subject(String id, String tenant, Set<Scope> scopes, boolean authenticated) {

  public Subject {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(tenant, "tenant must not be null");
    scopes = Set.copyOf(scopes);
    if (scopes.stream().anyMatch(s -> !s.tenant().equals(tenant))) {
      throw new IllegalArgumentException("a subject's scopes must be in its own tenant");
    }
  }
}

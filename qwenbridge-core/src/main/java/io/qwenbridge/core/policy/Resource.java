package io.qwenbridge.core.policy;

import java.util.Objects;
import java.util.Optional;

/**
 * What an operation acts on (QB-071, QB-073). Every resource belongs to exactly one tenant.
 *
 * @param type resource type, e.g. {@code index}, {@code pack}, {@code license}, {@code plugin}
 * @param domain domain such as {@code commerce}, when the resource belongs to one
 * @param connector connector or plugin id, when the operation runs through one
 * @param id resource identifier, when it names one
 */
public record Resource(
    String type,
    String tenant,
    Optional<String> domain,
    Optional<String> connector,
    Optional<String> id) {

  public Resource {
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(tenant, "tenant must not be null");
    domain = domain == null ? Optional.empty() : domain;
    connector = connector == null ? Optional.empty() : connector;
    id = id == null ? Optional.empty() : id;
  }

  public static Resource of(String type, String tenant) {
    return new Resource(type, tenant, Optional.empty(), Optional.empty(), Optional.empty());
  }

  public Resource inDomain(String domain) {
    return new Resource(type, tenant, Optional.ofNullable(domain), connector, id);
  }

  public Resource via(String connector) {
    return new Resource(type, tenant, domain, Optional.ofNullable(connector), id);
  }

  public Resource named(String id) {
    return new Resource(type, tenant, domain, connector, Optional.ofNullable(id));
  }
}

package io.qwenbridge.core.validation;

import java.util.List;
import java.util.Objects;

/** The mapping rules a Domain Pack approves for one schema (QB-053). */
public record DomainMapping(String schemaId, List<MappingRule> rules) {

  public DomainMapping {
    Objects.requireNonNull(schemaId, "schemaId must not be null");
    rules = rules == null ? List.of() : List.copyOf(rules);
  }

  public static DomainMapping none(String schemaId) {
    return new DomainMapping(schemaId, List.of());
  }
}

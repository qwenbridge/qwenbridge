package io.qwenbridge.core.recovery;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A typed, versioned relaxation policy (QB-126, QB-127). Rules are tried in order. A rule on a
 * protected field is refused when the policy is built: security and isolation constraints (tenant,
 * visibility, region, the currency of an amount, …) are never relaxable, whatever a configuration
 * says.
 *
 * @param maxProposals at most this many alternatives are offered for one query
 * @param lowResultThreshold see {@link ResultAssessment}; 0 means only zero results are recovered
 */
public record RelaxationPolicy(
    String id,
    String version,
    List<RelaxationRule> rules,
    Set<String> protectedFields,
    int maxProposals,
    int lowResultThreshold) {

  /** Always protected, whatever the policy adds. */
  public static final Set<String> ALWAYS_PROTECTED = Set.of("tenantId", "tenant", "domain");

  public RelaxationPolicy {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(version, "version must not be null");
    if (!id.matches("[a-z0-9][a-z0-9._-]{0,63}") || !version.matches("[0-9]+(\\.[0-9]+){0,2}")) {
      throw new IllegalArgumentException("policy id or version invalid");
    }
    rules = rules == null ? List.of() : List.copyOf(rules);
    var all = new HashSet<>(ALWAYS_PROTECTED);
    if (protectedFields != null) {
      all.addAll(protectedFields);
    }
    protectedFields = Set.copyOf(all);
    for (RelaxationRule rule : rules) {
      if (protectedFields.contains(rule.field())) {
        throw new IllegalArgumentException("protected field cannot be relaxed: " + rule.field());
      }
    }
    if (new HashSet<>(rules.stream().map(r -> r.field() + "/" + r.kind()).toList()).size()
        != rules.size()) {
      throw new IllegalArgumentException("duplicate relaxation rule");
    }
    if (rules.size() > 32 || maxProposals < 1 || maxProposals > 10 || lowResultThreshold < 0) {
      throw new IllegalArgumentException("policy bounds invalid");
    }
  }

  public String key() {
    return id + "@" + version;
  }
}

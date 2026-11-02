package io.qwenbridge.security;

import static io.qwenbridge.core.policy.OperationSpec.Effect.MUTATE;
import static io.qwenbridge.core.policy.OperationSpec.Effect.READ;
import static io.qwenbridge.core.policy.OperationSpec.Sensitivity.HIGH_RISK;
import static io.qwenbridge.core.policy.OperationSpec.Sensitivity.NORMAL;
import static io.qwenbridge.core.policy.OperationSpec.Sensitivity.SENSITIVE;

import io.qwenbridge.core.policy.OperationCatalog;
import io.qwenbridge.core.policy.OperationSpec;
import io.qwenbridge.core.policy.PolicyDecision;
import io.qwenbridge.core.policy.PolicyEngine;
import io.qwenbridge.core.policy.ReasonCode;
import io.qwenbridge.core.policy.Resource;
import io.qwenbridge.core.policy.Scope;
import io.qwenbridge.core.policy.Subject;
import jakarta.ws.rs.ForbiddenException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * The single enforcement point for every authorization check in the server (QB-071–QB-076). {@link
 * ApiIdentity.Principal#require} delegates here, so every endpoint, connector call and plugin
 * invocation is decided by one Core {@link PolicyEngine}. There is no code path that grants access
 * without it.
 *
 * <p>Until the configured engine is installed at startup ({@link CentralPolicy}), a default engine
 * with the same catalog, no extra rules and approvals reported but not enforced applies. It is
 * never a pass-through: unknown operations, missing scopes and other tenants are still denied.
 */
public final class Authorization {

  /** Every operation the server performs, classified by effect and sensitivity (QB-074). */
  public static final OperationCatalog CATALOG =
      new OperationCatalog(
          List.of(
              new OperationSpec("search", READ, NORMAL),
              new OperationSpec("packs:read", READ, SENSITIVE),
              new OperationSpec("plugins:read", READ, SENSITIVE),
              new OperationSpec("analytics:read", READ, SENSITIVE),
              new OperationSpec("evidence:read", READ, SENSITIVE),
              new OperationSpec("debug:read", READ, SENSITIVE),
              new OperationSpec("agents:read", READ, SENSITIVE),
              new OperationSpec("agents:run", READ, SENSITIVE),
              new OperationSpec("catalog:write", MUTATE, SENSITIVE),
              new OperationSpec("schema:write", MUTATE, SENSITIVE),
              new OperationSpec("evidence:write", MUTATE, SENSITIVE),
              new OperationSpec("approvals:grant", MUTATE, SENSITIVE),
              new OperationSpec("packs:write", MUTATE, HIGH_RISK),
              new OperationSpec("license:write", MUTATE, HIGH_RISK),
              new OperationSpec("marketplace:publish", MUTATE, HIGH_RISK)));

  private static final Map<String, String> RESOURCE_TYPES =
      Map.ofEntries(
          Map.entry("search", "index"),
          Map.entry("packs:read", "pack"),
          Map.entry("packs:write", "pack"),
          Map.entry("plugins:read", "plugin"),
          Map.entry("analytics:read", "analytics"),
          Map.entry("evidence:read", "evidence"),
          Map.entry("evidence:write", "evidence"),
          Map.entry("debug:read", "debug"),
          Map.entry("agents:read", "agent"),
          Map.entry("agents:run", "agent"),
          Map.entry("catalog:write", "catalog"),
          Map.entry("schema:write", "schema"),
          Map.entry("approvals:grant", "approval"),
          Map.entry("license:write", "license"),
          Map.entry("marketplace:publish", "listing"));

  /** Decides one request; installed by {@link CentralPolicy}. */
  @FunctionalInterface
  public interface Authorizer {
    PolicyDecision decide(ApiIdentity.Principal caller, String operation, Resource resource);

    /**
     * Decides with request attributes for domain policy rules, and returns the domain-pack outcome
     * the engine's pack rule produced (empty when the engine stopped earlier, for example for a
     * missing scope, or when no pack rule is installed).
     */
    default Evaluation evaluate(
        ApiIdentity.Principal caller,
        String operation,
        Resource resource,
        Map<String, Object> attributes) {
      return new Evaluation(decide(caller, operation, resource), java.util.Optional.empty());
    }
  }

  /** The central decision and the domain-pack outcome that contributed to it. */
  public record Evaluation(
      PolicyDecision decision,
      java.util.Optional<io.qwenbridge.platform.PackPolicies.Outcome> packs) {}

  /**
   * QB-151: domain-pack policy for an operation in a domain, evaluated by the central engine (the
   * single policy path) with the request's attributes. The result is the pack outcome with its
   * traces; when the engine denies before any pack rule runs (no scope, another tenant), the
   * outcome is that denial.
   */
  public static io.qwenbridge.platform.PackPolicies.Outcome domainPolicies(
      ApiIdentity.Principal caller,
      String domain,
      String operation,
      Map<String, Object> attributes) {
    Evaluation evaluation =
        CURRENT
            .get()
            .evaluate(
                caller,
                operation,
                resourceFor(operation, caller.tenant()).inDomain(domain),
                attributes);
    PolicyDecision decision = evaluation.decision();
    if (evaluation.packs().isPresent()) {
      var packs = evaluation.packs().get();
      if (decision.allowed() || !packs.allowed()) return packs;
      if (decision.outcome() == PolicyDecision.Outcome.REQUIRES_APPROVAL) return packs;
    }
    if (decision.allowed())
      return new io.qwenbridge.platform.PackPolicies.Outcome("ALLOW", false, List.of());
    return new io.qwenbridge.platform.PackPolicies.Outcome(
        "DENY",
        false,
        List.of(
            new io.qwenbridge.platform.PackPolicies.Trace(
                "central-policy",
                "1.0.0",
                0,
                decision.reasons().stream().map(Enum::name).sorted().toList().toString(),
                "DENY",
                "The central policy engine denied the operation")));
  }

  /** {@link #domainPolicies}, throwing 403 unless the outcome allows execution. */
  public static io.qwenbridge.platform.PackPolicies.Outcome requireDomainPolicies(
      ApiIdentity.Principal caller,
      String domain,
      String operation,
      Map<String, Object> attributes) {
    var outcome = domainPolicies(caller, domain, operation, attributes);
    if (!outcome.allowed())
      throw new ForbiddenException(
          "Policy stopped execution: "
              + outcome.decisions().stream()
                  .map(io.qwenbridge.platform.PackPolicies.Trace::reason)
                  .toList());
    return outcome;
  }

  private static final AtomicReference<Authorizer> CURRENT =
      new AtomicReference<>(defaultAuthorizer());

  private Authorization() {}

  static Authorizer defaultAuthorizer() {
    PolicyEngine engine =
        new PolicyEngine(CATALOG, List::of, Clock.systemUTC(), false, Duration.ofHours(1));
    return (caller, operation, resource) ->
        engine.decide(
            io.qwenbridge.core.policy.PolicyRequest.of(subject(caller), operation, resource));
  }

  static void install(Authorizer authorizer) {
    CURRENT.set(authorizer);
  }

  /** The resource type an operation acts on, for checks that do not name a specific resource. */
  public static Resource resourceFor(String operation, String tenant) {
    return Resource.of(RESOURCE_TYPES.getOrDefault(operation, "unknown"), tenant);
  }

  /** The central decision, without throwing (QB-227: tool boundaries record denials as results). */
  public static PolicyDecision decide(
      ApiIdentity.Principal caller, String operation, Resource resource) {
    return CURRENT.get().decide(caller, operation, resource);
  }

  /** Throws 403 unless the central engine allows the operation on the resource. */
  public static PolicyDecision require(
      ApiIdentity.Principal caller, String operation, Resource resource) {
    PolicyDecision decision = CURRENT.get().decide(caller, operation, resource);
    if (decision.allowed()) {
      return decision;
    }
    if (decision.outcome() == PolicyDecision.Outcome.REQUIRES_APPROVAL) {
      throw new ForbiddenException("Approval required for operation: " + operation);
    }
    if (decision.reasons().contains(ReasonCode.NO_SCOPE)) {
      throw new ForbiddenException("Missing permission: " + operation);
    }
    throw new ForbiddenException(
        "Operation not permitted: " + operation + " " + decision.reasons());
  }

  /** The Core subject for a principal: one tenant-bound scope per granted operation. */
  public static Subject subject(ApiIdentity.Principal caller) {
    Set<Scope> scopes =
        caller.roles().stream()
            .filter(role -> !role.isBlank() && !role.contains("*"))
            .map(role -> Scope.of(role, caller.tenant()))
            .collect(Collectors.toSet());
    return new Subject(caller.subject(), caller.tenant(), scopes, !caller.anonymous());
  }
}

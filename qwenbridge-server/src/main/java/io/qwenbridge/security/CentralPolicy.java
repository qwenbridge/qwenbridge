package io.qwenbridge.security;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableContext;
import io.quarkus.runtime.StartupEvent;
import io.qwenbridge.core.policy.Approval;
import io.qwenbridge.core.policy.PolicyDecision;
import io.qwenbridge.core.policy.PolicyEngine;
import io.qwenbridge.core.policy.PolicyRequest;
import io.qwenbridge.core.policy.PolicyRule;
import io.qwenbridge.core.policy.ReasonCode;
import io.qwenbridge.core.policy.Resource;
import io.qwenbridge.platform.PackPolicies;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Installs the configured central policy engine at startup (QB-071–QB-076).
 *
 * <ul>
 *   <li>Domain Pack policies run as a {@link PolicyRule} for requests in a domain. They can deny or
 *       ask for approval, never grant (audit S2: without packs, the base scope decision stands).
 *   <li>Approvals come from {@link ApprovalStore}, named by the {@value ApprovalContext#HEADER}
 *       header, and are consumed when they authorize a request.
 *   <li>{@code qwenbridge.policy.approvals-enforced} (default false) decides whether a missing
 *       approval blocks a high-risk operation; when false it is allowed and logged.
 * </ul>
 */
@ApplicationScoped
public class CentralPolicy {

  private static final Logger LOG = Logger.getLogger(CentralPolicy.class);

  private final PackPolicies packs;
  private final io.qwenbridge.domains.DomainPacks domains;
  private final ApprovalStore approvals;
  private final boolean enforced;
  private final Duration maxValidity;

  public CentralPolicy(
      PackPolicies packs,
      io.qwenbridge.domains.DomainPacks domains,
      ApprovalStore approvals,
      @ConfigProperty(name = "qwenbridge.policy.approvals-enforced", defaultValue = "false")
          boolean enforced,
      @ConfigProperty(name = "qwenbridge.policy.approval-max-validity", defaultValue = "PT1H")
          Duration maxValidity) {
    this.packs = packs;
    this.domains = domains;
    this.approvals = approvals;
    this.enforced = enforced;
    this.maxValidity = maxValidity;
  }

  void onStart(@Observes StartupEvent event) {
    PolicyEngine engine =
        new PolicyEngine(
            Authorization.CATALOG,
            () ->
                java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(packRule()), domains.rules().stream())
                    .toList(),
            Clock.systemUTC(),
            enforced,
            maxValidity);
    Authorization.install(
        new Authorization.Authorizer() {
          @Override
          public PolicyDecision decide(
              ApiIdentity.Principal caller, String operation, Resource resource) {
            return evaluate(caller, operation, resource, Map.of()).decision();
          }

          @Override
          public Authorization.Evaluation evaluate(
              ApiIdentity.Principal caller,
              String operation,
              Resource resource,
              Map<String, Object> attributes) {
            var call = new Call(attributes);
            CALL.set(call);
            try {
              return new Authorization.Evaluation(
                  CentralPolicy.this.decide(engine, caller, operation, resource),
                  Optional.ofNullable(call.outcome));
            } finally {
              CALL.remove();
            }
          }
        });
    LOG.infof(
        "Central policy installed: %d operations, approvals %s",
        Authorization.CATALOG.all().size(), enforced ? "enforced" : "reported only");
  }

  /** Restores the default engine so nothing refers to this application's beans after shutdown. */
  void onStop(@Observes io.quarkus.runtime.ShutdownEvent event) {
    Authorization.install(Authorization.defaultAuthorizer());
  }

  private PolicyDecision decide(
      PolicyEngine engine, ApiIdentity.Principal caller, String operation, Resource resource) {
    Optional<Approval> approval = currentApprovalId().flatMap(approvals::find);
    PolicyDecision decision =
        engine.decide(
            new PolicyRequest(
                Authorization.subject(caller), operation, resource, context(), approval));
    if (decision.allowed() && decision.reasons().contains(ReasonCode.APPROVED)) {
      approval.ifPresent(a -> approvals.consume(a.id()));
    }
    if (decision.reasons().contains(ReasonCode.APPROVAL_NOT_ENFORCED)) {
      LOG.infof(
          "High-risk operation %s allowed without approval (approvals not enforced)", operation);
    }
    return decision;
  }

  /** QB-235: scalar request attributes, as the context domain pack rules see. */
  private static Map<String, String> context() {
    Call call = CALL.get();
    if (call == null) return Map.of();
    Map<String, String> out = new java.util.TreeMap<>();
    call.attributes.forEach(
        (key, value) -> {
          if (value instanceof String || value instanceof Number || value instanceof Boolean)
            out.put(key, String.valueOf(value));
        });
    return out;
  }

  private static Optional<String> currentApprovalId() {
    var container = Arc.container();
    if (container == null) {
      return Optional.empty();
    }
    InjectableContext context = container.requestContext();
    if (!context.isActive()) {
      return Optional.empty();
    }
    return container.instance(ApprovalContext.class).get().approvalId();
  }

  /** Attributes of the evaluation in progress and the pack outcome it produced (same thread). */
  private static final class Call {
    final Map<String, Object> attributes;
    PackPolicies.Outcome outcome;

    Call(Map<String, Object> attributes) {
      this.attributes = attributes == null ? Map.of() : attributes;
    }
  }

  private static final ThreadLocal<Call> CALL = new ThreadLocal<>();

  private PolicyRule packRule() {
    return new PolicyRule() {
      @Override
      public String id() {
        return "domain-pack-policies";
      }

      @Override
      public Verdict evaluate(PolicyRequest request) {
        if (request.resource().domain().isEmpty()) {
          return Verdict.NOT_APPLICABLE;
        }
        var subject = request.subject();
        var principal =
            new ApiIdentity.Principal(
                subject.id(),
                subject.tenant(),
                subject.scopes().stream().map(s -> s.operation()).collect(Collectors.toSet()));
        Call call = CALL.get();
        var outcome =
            packs.evaluate(
                principal,
                request.resource().domain().get(),
                request.operation(),
                call == null ? Map.of() : call.attributes);
        if (call != null) call.outcome = outcome;
        if (!outcome.allowed())
          LOG.infof(
              "Policy packs %s decided %s for %s in domain %s",
              outcome.versions(),
              outcome.effect(),
              request.operation(),
              request.resource().domain().get());
        return switch (outcome.effect()) {
          case "DENY" -> Verdict.DENY;
          case "REVIEW" -> Verdict.REQUIRE_APPROVAL;
          default -> Verdict.NOT_APPLICABLE;
        };
      }
    };
  }
}

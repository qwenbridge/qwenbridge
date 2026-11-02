package io.qwenbridge.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.qwenbridge.core.policy.Approval;
import io.qwenbridge.core.policy.PolicyDecision;
import io.qwenbridge.core.policy.PolicyEngine;
import io.qwenbridge.core.policy.PolicyRequest;
import io.qwenbridge.core.policy.PolicyRule;
import io.qwenbridge.core.policy.ReasonCode;
import io.qwenbridge.core.policy.Resource;
import io.qwenbridge.domains.DomainPacks;
import io.qwenbridge.pack.PackManifest;
import io.qwenbridge.platform.PackAcceptanceTest;
import io.qwenbridge.platform.PackPolicies;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * QB-249 – QB-253: signed, independently compiled policy packs contribute rules to the central
 * engine without replacing it. Evaluation is deterministic, denial and approval take precedence
 * over ALLOW, invalid or incompatible packs are refused before activation, decisions name the pack
 * versions that made them, and disabling an optional pack never disables a Core check.
 */
class PolicyPackEnforcementTest {

  static String policy(int order, String domains, String operations, String effectExpression) {
    return "public int order(){return "
        + order
        + ";} public Set<String> domains(){return Set.of("
        + domains
        + ");} "
        + (operations == null
            ? ""
            : "public Set<String> operations(){return Set.of(" + operations + ");} ")
        + "public Decision evaluate(Input input){Object q=input.attributes().get(\"query\");"
        + "Object bulk=input.attributes().get(\"bulk\");"
        + effectExpression
        + "}";
  }

  static final String ALLOW_ALL =
      policy(
          1,
          "\"*\"",
          null,
          "return new Decision(Effect.ALLOW,\"allow-all\",\"Everything is fine\");");

  static String denyForbidden(String reason) {
    return policy(
        50,
        "\"commerce\"",
        null,
        "return q!=null&&q.toString().contains(\"forbidden\")?new Decision(Effect.DENY,"
            + "\"no-forbidden\",\""
            + reason
            + "\"):new Decision(Effect.ABSTAIN,\"no-forbidden\",\"Not applicable\");");
  }

  static final String REVIEW_BULK =
      policy(
          20,
          "\"*\"",
          "\"catalog:write\"",
          "return Boolean.TRUE.equals(bulk)?new Decision(Effect.REVIEW,\"bulk-review\",\"Bulk"
              + " changes need review\"):new Decision(Effect.ABSTAIN,\"bulk-review\",\"Not"
              + " bulk\");");

  static final ApiIdentity.Principal WRITER =
      new ApiIdentity.Principal("wendy", "tenant-a", Set.of("search", "catalog:write"));
  static final ApiIdentity.Principal READER =
      new ApiIdentity.Principal("ray", "tenant-a", Set.of("search"));

  static void install(PackAcceptanceTest.Fixture f, String id, String version, String body)
      throws Exception {
    f.packs.install(f.admin, f.artifact(id, version, body, PackManifest.Kind.POLICY, List.of()));
    f.packs.activate(f.admin, id, version, Map.of());
  }

  static CentralPolicy central(PackAcceptanceTest.Fixture f, boolean enforced) {
    var central =
        new CentralPolicy(
            new PackPolicies(f.packs),
            new DomainPacks(new io.qwenbridge.core.schema.SchemaRegistry()),
            new ApprovalStore(),
            enforced,
            Duration.ofHours(1));
    central.onStart(null);
    return central;
  }

  @Test
  void packsContributeRulesInADeterministicRestrictiveOrder() throws Exception {
    List<PackPolicies.Outcome> outcomes = new java.util.ArrayList<>();
    for (boolean reversed : List.of(false, true)) {
      try (var f = new PackAcceptanceTest.Fixture()) {
        f.enterprise();
        var order =
            new java.util.ArrayList<>(List.of("allow-all", "deny-forbidden", "review-bulk"));
        if (reversed) java.util.Collections.reverse(order);
        for (String id : order)
          install(
              f,
              id,
              "1.0.0",
              switch (id) {
                case "allow-all" -> ALLOW_ALL;
                case "deny-forbidden" -> denyForbidden("Forbidden items are not sold");
                default -> REVIEW_BULK;
              });
        central(f, true);
        try {
          var admin = new ApiIdentity.Principal("wendy", f.admin.tenant(), WRITER.roles());
          var denied =
              Authorization.domainPolicies(
                  admin, "commerce", "search", Map.of("query", "forbidden thing"));
          assertThat(denied.effect()).isEqualTo("DENY");
          assertThat(denied.conflict()).isTrue();
          assertThat(denied.decisions())
              .extracting(t -> t.order() + ":" + t.pack() + ":" + t.effect())
              .containsExactly("1:allow-all:ALLOW", "50:deny-forbidden:DENY");
          outcomes.add(denied);
          // REVIEW outranks ALLOW and applies only to the operations the pack names.
          var review =
              Authorization.domainPolicies(
                  admin, "commerce", "catalog:write", Map.of("bulk", true));
          assertThat(review.effect()).isEqualTo("REVIEW");
          assertThat(review.versions())
              .containsExactlyInAnyOrder(
                  "allow-all@1.0.0", "deny-forbidden@1.0.0", "review-bulk@1.0.0");
          var searchIgnoresReview =
              Authorization.domainPolicies(admin, "commerce", "search", Map.of("bulk", true));
          assertThat(searchIgnoresReview.effect()).isEqualTo("ALLOW");
          assertThat(searchIgnoresReview.versions())
              .containsExactlyInAnyOrder("allow-all@1.0.0", "deny-forbidden@1.0.0");
          // Without the bulk attribute the review rule abstains: the central decision is a plain
          // ALLOW.
          assertThat(
                  Authorization.decide(
                          admin,
                          "catalog:write",
                          Authorization.resourceFor("catalog:write", admin.tenant())
                              .inDomain("commerce"))
                      .outcome())
              .isEqualTo(PolicyDecision.Outcome.ALLOW);
        } finally {
          Authorization.install(Authorization.defaultAuthorizer());
        }
      }
    }
    // Same inputs and versions, packs installed in the opposite order: the same decision.
    assertThat(outcomes.get(0)).isEqualTo(outcomes.get(1));
  }

  @Test
  void invalidOrIncompatiblePacksAreRefusedBeforeActivation() throws Exception {
    try (var f = new PackAcceptanceTest.Fixture()) {
      f.enterprise();
      String unknownOperation =
          policy(
              1, "\"*\"", "\"everything:delete\"", "return new Decision(Effect.DENY,\"x\",\"x\");");
      String futureContract =
          "public int contract(){return 2;} "
              + policy(1, "\"*\"", null, "return new Decision(Effect.DENY,\"x\",\"x\");");
      String outOfRange =
          policy(99_999, "\"*\"", null, "return new Decision(Effect.DENY,\"x\",\"x\");");
      String noDomains = policy(1, "", null, "return new Decision(Effect.DENY,\"x\",\"x\");");
      for (var bad :
          Map.of(
                  "bad-operation", unknownOperation,
                  "bad-contract", futureContract,
                  "bad-order", outOfRange,
                  "bad-domains", noDomains)
              .entrySet()) {
        var artifact =
            f.artifact(bad.getKey(), "1.0.0", bad.getValue(), PackManifest.Kind.POLICY, List.of());
        assertThatThrownBy(() -> f.packs.install(f.admin, artifact))
            .as(bad.getKey())
            .hasMessageContaining("Pack cannot be loaded")
            .rootCause()
            .hasMessageMatching(
                "(Policy pack names an unsupported operation: everything:delete"
                    + "|Unsupported policy contract 2 \\(runtime: 1\\)"
                    + "|Policy order must be within -10000\\.\\.10000"
                    + "|A policy pack must name its domains)");
        assertThatThrownBy(() -> f.packs.activate(f.admin, bad.getKey(), "1.0.0", Map.of()))
            .hasMessageContaining("not installed");
      }
      assertThat(f.packs.list(f.admin.tenant()))
          .noneMatch(v -> v.manifest().id().startsWith("bad-"));
    }
  }

  @Test
  void decisionsNameTheirVersionsAcrossUpgradeAndRollback() throws Exception {
    try (var f = new PackAcceptanceTest.Fixture()) {
      f.enterprise();
      install(f, "deny-forbidden", "1.0.0", denyForbidden("Version one"));
      var packs = new PackPolicies(f.packs);
      var attributes = Map.<String, Object>of("query", "forbidden");
      assertThat(packs.evaluate(f.admin, "commerce", "search", attributes).versions())
          .containsExactly("deny-forbidden@1.0.0");
      install(f, "deny-forbidden", "1.1.0", denyForbidden("Version two"));
      var upgraded = packs.evaluate(f.admin, "commerce", "search", attributes);
      assertThat(upgraded.versions()).containsExactly("deny-forbidden@1.1.0");
      assertThat(upgraded.decisions().getFirst().reason()).isEqualTo("Version two");
      f.packs.rollback(f.admin, "deny-forbidden");
      var rolledBack = packs.evaluate(f.admin, "commerce", "search", attributes);
      assertThat(rolledBack.versions()).containsExactly("deny-forbidden@1.0.0");
      assertThat(rolledBack.decisions().getFirst().reason()).isEqualTo("Version one");
    }
  }

  @Test
  void disablingAnOptionalPackNeverDisablesCoreSecurity() throws Exception {
    try (var f = new PackAcceptanceTest.Fixture()) {
      f.enterprise();
      install(f, "allow-all", "1.0.0", ALLOW_ALL);
      install(f, "deny-forbidden", "1.0.0", denyForbidden("Forbidden"));
      central(f, true);
      try {
        var tenant = f.admin.tenant();
        var reader = new ApiIdentity.Principal("ray", tenant, Set.of("search"));
        assertThat(
                Authorization.domainPolicies(
                        reader, "commerce", "search", Map.of("query", "forbidden"))
                    .allowed())
            .isFalse();
        // Unauthorized lifecycle attempts are refused by the Core boundary.
        assertThatThrownBy(() -> f.packs.disable(reader, "deny-forbidden"))
            .isInstanceOf(jakarta.ws.rs.ForbiddenException.class);
        assertThatThrownBy(() -> f.packs.activate(reader, "allow-all", "1.0.0", Map.of()))
            .isInstanceOf(jakarta.ws.rs.ForbiddenException.class);

        // Changing packs is high-risk: with approvals enforced even the operator needs one.

        assertThatThrownBy(() -> f.packs.disable(f.admin, "deny-forbidden"))
            .hasMessageContaining("Approval required");

        Authorization.install(Authorization.defaultAuthorizer());

        f.packs.disable(f.admin, "deny-forbidden");

        central(f, true);
        assertThat(
                Authorization.domainPolicies(
                        reader, "commerce", "search", Map.of("query", "forbidden"))
                    .allowed())
            .isTrue();
        Authorization.install(Authorization.defaultAuthorizer());
        f.packs.disable(f.admin, "allow-all");
        central(f, true);
        // With every optional pack disabled the Core checks still stand; an ALLOW pack never
        // stood in for them either.
        var commerce = Authorization.resourceFor("catalog:write", tenant).inDomain("commerce");
        assertThat(Authorization.decide(reader, "catalog:write", commerce).reasons())
            .containsExactly(ReasonCode.NO_SCOPE);
        var outsider = new ApiIdentity.Principal("eve", "tenant-b", Set.of("search"));
        assertThat(
                Authorization.decide(
                        outsider, "search", Resource.of("index", tenant).inDomain("commerce"))
                    .reasons())
            .containsExactly(ReasonCode.TENANT_MISMATCH);
        assertThat(
                Authorization.decide(
                        f.admin, "packs:write", Authorization.resourceFor("packs:write", tenant))
                    .outcome())
            .isEqualTo(PolicyDecision.Outcome.REQUIRES_APPROVAL);
        assertThat(
                Authorization.domainPolicies(reader, "commerce", "catalog:write", Map.of())
                    .allowed())
            .isFalse();
      } finally {
        Authorization.install(Authorization.defaultAuthorizer());
      }
    }
  }

  @Test
  void anApprovalNeverOverridesADenial() {
    var tenant = "tenant-a";
    var resource = Resource.of("catalog", tenant).inDomain("commerce");
    PolicyRule deny =
        new PolicyRule() {
          public String id() {
            return "pack:deny-forbidden@1.0.0";
          }

          public Verdict evaluate(PolicyRequest request) {
            return Verdict.DENY;
          }
        };
    var engine =
        new PolicyEngine(
            Authorization.CATALOG,
            () -> List.of(deny),
            Clock.systemUTC(),
            true,
            Duration.ofHours(1));
    var approval =
        new Approval(
            "a-1",
            "boss",
            "catalog:write",
            resource,
            Instant.now().minusSeconds(5),
            Instant.now().plusSeconds(600));
    var decision =
        engine.decide(
            new PolicyRequest(
                Authorization.subject(WRITER),
                "catalog:write",
                resource,
                Map.of(),
                Optional.of(approval)));
    assertThat(decision.outcome()).isEqualTo(PolicyDecision.Outcome.DENY);
    assertThat(decision.reasons()).containsExactly(ReasonCode.POLICY_DENY);
    assertThat(decision.policyIds()).containsExactly("pack:deny-forbidden@1.0.0");
  }
}

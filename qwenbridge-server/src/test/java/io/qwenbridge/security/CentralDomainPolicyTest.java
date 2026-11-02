package io.qwenbridge.security;

import static org.assertj.core.api.Assertions.*;

import io.qwenbridge.pack.PackManifest;
import io.qwenbridge.platform.PackAcceptanceTest;
import io.qwenbridge.platform.PackPolicies;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * QB-151: domain-pack policies run inside the central policy engine, with the request's attributes,
 * and Commerce reads their outcome from there. Real signed, independently compiled policy packs are
 * installed and evaluated.
 */
class CentralDomainPolicyTest {

  private static String policy(String effectWhenForbidden) {
    return "public int order(){return 10;} public Set<String> domains(){return"
        + " Set.of(\"commerce\");} public Decision evaluate(Input input){"
        + "Object q=input.attributes().get(\"query\");"
        + "return q!=null&&q.toString().contains(\"forbidden\")"
        + "?new Decision(Effect."
        + effectWhenForbidden
        + ",\"no-forbidden\",\"The query names a forbidden item\")"
        + ":new Decision(Effect.ALLOW,\"default\",\"Allowed\");}";
  }

  @Test
  void packPoliciesAreEvaluatedByTheCentralEngineWithAttributes() throws Exception {
    try (var f = new PackAcceptanceTest.Fixture()) {
      f.enterprise();
      f.packs.install(
          f.admin,
          f.artifact(
              "policy-forbidden", "1.0.0", policy("DENY"), PackManifest.Kind.POLICY, List.of()));
      f.packs.activate(f.admin, "policy-forbidden", "1.0.0", Map.of());
      var central =
          new CentralPolicy(
              new PackPolicies(f.packs),
              new io.qwenbridge.domains.DomainPacks(new io.qwenbridge.core.schema.SchemaRegistry()),
              new ApprovalStore(),
              false,
              Duration.ofHours(1));
      central.onStart(null);
      try {
        var denied =
            Authorization.domainPolicies(
                f.admin, "commerce", "search", Map.of("query", "a forbidden item"));
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.decisions())
            .extracting(PackPolicies.Trace::rule)
            .contains("no-forbidden");
        var allowed =
            Authorization.domainPolicies(
                f.admin, "commerce", "search", Map.of("query", "running shoes"));
        assertThat(allowed.allowed()).isTrue();
        assertThat(allowed.decisions())
            .extracting(PackPolicies.Trace::pack)
            .contains("policy-forbidden");
        assertThatThrownBy(
                () ->
                    Authorization.requireDomainPolicies(
                        f.admin, "commerce", "search", Map.of("query", "forbidden")))
            .isInstanceOf(jakarta.ws.rs.ForbiddenException.class);
        // Without the scope the engine denies before any pack runs.
        var noScope =
            new ApiIdentity.Principal("viewer", f.admin.tenant(), Set.of("analytics:read"));
        var central403 = Authorization.domainPolicies(noScope, "commerce", "search", Map.of());
        assertThat(central403.allowed()).isFalse();
        assertThat(central403.decisions())
            .extracting(PackPolicies.Trace::pack)
            .containsExactly("central-policy");
        // The plain authorization path still consults the pack rule (no attributes).
        assertThat(
                Authorization.require(
                        f.admin,
                        "search",
                        Authorization.resourceFor("search", f.admin.tenant()).inDomain("commerce"))
                    .allowed())
            .isTrue();
      } finally {
        central.onStop(null);
      }
    }
  }

  @Test
  void aReviewVerdictStillStopsExecutionWhileApprovalsAreNotEnforced() throws Exception {
    try (var f = new PackAcceptanceTest.Fixture()) {
      f.enterprise();
      f.packs.install(
          f.admin,
          f.artifact(
              "policy-review", "1.0.0", policy("REVIEW"), PackManifest.Kind.POLICY, List.of()));
      f.packs.activate(f.admin, "policy-review", "1.0.0", Map.of());
      var central =
          new CentralPolicy(
              new PackPolicies(f.packs),
              new io.qwenbridge.domains.DomainPacks(new io.qwenbridge.core.schema.SchemaRegistry()),
              new ApprovalStore(),
              false,
              Duration.ofHours(1));
      central.onStart(null);
      try {
        var review =
            Authorization.domainPolicies(
                f.admin, "commerce", "search", Map.of("query", "forbidden"));
        assertThat(review.effect()).isEqualTo("REVIEW");
        assertThat(review.allowed()).isFalse();
      } finally {
        central.onStop(null);
      }
    }
  }
}

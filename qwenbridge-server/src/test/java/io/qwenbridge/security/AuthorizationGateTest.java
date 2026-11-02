package io.qwenbridge.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.qwenbridge.core.policy.ReasonCode;
import io.qwenbridge.core.policy.Resource;
import io.qwenbridge.security.ApiIdentity.Principal;
import jakarta.ws.rs.ForbiddenException;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * QB-072–QB-077 at the server's single enforcement point, with the default engine that applies
 * before (and without) startup configuration: it is never a pass-through.
 */
class AuthorizationGateTest {

  static final Principal ALICE =
      new Principal("alice", "acme", Set.of("search", "catalog:write", "packs:write"));

  @Test
  void unknownOperationsAreDeniedEvenIfARoleWithThatNameIsHeld() {
    Principal odd = new Principal("eve", "acme", Set.of("everything:delete"));
    assertThatThrownBy(() -> odd.require("everything:delete"))
        .isInstanceOf(ForbiddenException.class)
        .hasMessageContaining("UNKNOWN_OPERATION");
  }

  @Test
  void missingScopeKeepsTheReleasedMessage() {
    assertThatThrownBy(() -> ALICE.require("analytics:read"))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage("Missing permission: analytics:read");
  }

  @Test
  void anonymousPrincipalsNeverReachSensitiveOperations() {
    Principal anonymous = new Principal("anonymous", "public", Set.of("search", "catalog:write"));
    anonymous.require("search");
    assertThatThrownBy(() -> anonymous.require("catalog:write"))
        .hasMessageContaining("UNAUTHENTICATED");
  }

  @Test
  void connectorsAndPluginsCannotReachAnotherTenant() {
    Resource otherTenantIndex =
        Resource.of("index", "globex").inDomain("commerce").via("postgresql");
    assertThatThrownBy(() -> ALICE.require("search", otherTenantIndex))
        .hasMessageContaining("TENANT_MISMATCH");
    ALICE.require("search", Resource.of("index", "acme").inDomain("commerce").via("postgresql"));
  }

  @Test
  void highRiskOperationsReportTheirApprovalRequirementWhenNotEnforced() {
    var decision =
        Authorization.require(
            ALICE, "packs:write", Authorization.resourceFor("packs:write", "acme"));
    assertThat(decision.allowed()).isTrue();
    assertThat(decision.reasons()).contains(ReasonCode.APPROVAL_NOT_ENFORCED);
    assertThat(decision.effect()).isEqualTo(io.qwenbridge.core.policy.OperationSpec.Effect.MUTATE);
  }

  @Test
  void packPoliciesNeverStandInForAuthorization() {
    var packs = new io.qwenbridge.platform.PackPolicies(null);
    var outcome =
        packs.evaluate(
            new Principal("bob", "acme", Set.of()), "commerce", "search", java.util.Map.of());
    assertThat(outcome.effect()).isEqualTo("DENY");
    assertThat(outcome.decisions().getFirst().rule()).isEqualTo("no-scope");
  }

  @Test
  void everyOperationTheServerChecksIsClassified() {
    for (String op :
        java.util.List.of(
            "search",
            "packs:read",
            "plugins:read",
            "analytics:read",
            "evidence:read",
            "debug:read",
            "catalog:write",
            "schema:write",
            "evidence:write",
            "approvals:grant",
            "packs:write",
            "license:write",
            "marketplace:publish")) {
      assertThat(Authorization.CATALOG.find(op)).as(op).isPresent();
    }
  }
}

package io.qwenbridge.core.policy;

import static io.qwenbridge.core.policy.OperationSpec.Effect.MUTATE;
import static io.qwenbridge.core.policy.OperationSpec.Effect.READ;
import static io.qwenbridge.core.policy.OperationSpec.Sensitivity.HIGH_RISK;
import static io.qwenbridge.core.policy.OperationSpec.Sensitivity.NORMAL;
import static io.qwenbridge.core.policy.OperationSpec.Sensitivity.SENSITIVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.core.policy.PolicyDecision.Outcome;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** QB-071–QB-077: default deny, scopes, tenant isolation, approvals and bypass attempts. */
class PolicyEngineTest {

  static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
  static final OperationCatalog CATALOG =
      new OperationCatalog(
          List.of(
              new OperationSpec("search", READ, NORMAL),
              new OperationSpec("analytics:read", READ, SENSITIVE),
              new OperationSpec("catalog:write", MUTATE, SENSITIVE),
              new OperationSpec("packs:write", MUTATE, HIGH_RISK)));

  static PolicyEngine engine(boolean enforced, PolicyRule... rules) {
    return new PolicyEngine(
        CATALOG,
        () -> List.of(rules),
        Clock.fixed(NOW, ZoneOffset.UTC),
        enforced,
        Duration.ofHours(1));
  }

  static Subject user(String id, String tenant, String... operations) {
    return new Subject(
        id,
        tenant,
        java.util.Arrays.stream(operations)
            .map(op -> Scope.of(op, tenant))
            .collect(java.util.stream.Collectors.toSet()),
        true);
  }

  static final Subject ALICE = user("alice", "acme", "search", "catalog:write", "packs:write");
  static final Resource PACK = Resource.of("pack", "acme").named("commerce-pack");

  static PolicyDecision decide(PolicyEngine engine, Subject s, String op, Resource r) {
    return engine.decide(PolicyRequest.of(s, op, r));
  }

  static Approval approval(String approver, Instant issued, Instant expires) {
    return new Approval("ap-1", approver, "packs:write", PACK, issued, expires);
  }

  @Nested
  class DefaultDeny {

    @Test
    void unknownOperationsAreDenied() {
      PolicyDecision d = decide(engine(false), ALICE, "packs:delete-everything", PACK);
      assertEquals(Outcome.DENY, d.outcome());
      assertEquals(List.of(ReasonCode.UNKNOWN_OPERATION), d.reasons());
    }

    @Test
    void anOperationWithoutAScopeIsDenied() {
      PolicyDecision d =
          decide(engine(false), ALICE, "analytics:read", Resource.of("analytics", "acme"));
      assertEquals(List.of(ReasonCode.NO_SCOPE), d.reasons());
    }

    @Test
    void anonymousSubjectsGetOnlyNormalOperationsTheyHoldAScopeFor() {
      Subject anon =
          new Subject(
              "anonymous",
              "public",
              Set.of(Scope.of("search", "public"), Scope.of("catalog:write", "public")),
              false);
      assertTrue(decide(engine(false), anon, "search", Resource.of("index", "public")).allowed());
      assertEquals(
          List.of(ReasonCode.UNAUTHENTICATED),
          decide(engine(false), anon, "catalog:write", Resource.of("index", "public")).reasons());
    }
  }

  @Nested
  class TenantIsolation {

    @Test
    void aResourceInAnotherTenantIsDeniedEvenWithTheScope() {
      PolicyDecision d = decide(engine(false), ALICE, "search", Resource.of("index", "globex"));
      assertEquals(List.of(ReasonCode.TENANT_MISMATCH), d.reasons());
    }

    @Test
    void scopesCannotNameAnotherTenantOrUseWildcards() {
      assertThrows(
          IllegalArgumentException.class,
          () -> new Subject("eve", "acme", Set.of(Scope.of("search", "globex")), true));
      assertThrows(IllegalArgumentException.class, () -> Scope.of("*", "acme"));
      assertThrows(IllegalArgumentException.class, () -> Scope.of("search", "*"));
    }

    @Test
    void narrowedScopesOnlyCoverTheirConnectorAndDomain() {
      Scope onlyPostgres =
          new Scope(
              "search",
              "acme",
              Optional.of("commerce"),
              Optional.of("postgresql"),
              Optional.empty());
      Subject bob = new Subject("bob", "acme", Set.of(onlyPostgres), true);
      Resource viaPostgres = Resource.of("index", "acme").inDomain("commerce").via("postgresql");
      Resource viaMongo = Resource.of("index", "acme").inDomain("commerce").via("mongodb");
      assertTrue(decide(engine(false), bob, "search", viaPostgres).allowed());
      assertEquals(
          List.of(ReasonCode.NO_SCOPE), decide(engine(false), bob, "search", viaMongo).reasons());
    }
  }

  @Nested
  class RulesOnlyRestrict {

    @Test
    void aRuleCannotGrantWhatTheBaseRefused() {
      PolicyRule permissive =
          new PolicyRule() {
            public String id() {
              return "permissive-pack";
            }

            public Verdict evaluate(PolicyRequest r) {
              return Verdict.NOT_APPLICABLE; // There is no ALLOW verdict to return.
            }
          };
      assertEquals(
          Outcome.DENY,
          decide(engine(false, permissive), ALICE, "search", Resource.of("index", "globex"))
              .outcome());
    }

    @Test
    void aDenyingOrFailingRuleDenies() {
      PolicyRule deny =
          new PolicyRule() {
            public String id() {
              return "no-search";
            }

            public Verdict evaluate(PolicyRequest r) {
              return Verdict.DENY;
            }
          };
      PolicyRule broken =
          new PolicyRule() {
            public String id() {
              return "broken";
            }

            public Verdict evaluate(PolicyRequest r) {
              throw new IllegalStateException("boom");
            }
          };
      Resource index = Resource.of("index", "acme");
      PolicyDecision denied = decide(engine(false, deny), ALICE, "search", index);
      assertEquals(List.of(ReasonCode.POLICY_DENY), denied.reasons());
      assertEquals(List.of("no-search"), denied.policyIds());
      assertEquals(
          List.of(ReasonCode.POLICY_ERROR),
          decide(engine(false, broken), ALICE, "search", index).reasons());
    }

    @Test
    void withoutRulesTheBaseDecisionStandsAndSaysWhy() {
      PolicyDecision d =
          decide(engine(false), ALICE, "catalog:write", Resource.of("index", "acme"));
      assertTrue(d.allowed());
      assertEquals(List.of(ReasonCode.ALLOWED_BY_SCOPE), d.reasons());
      assertEquals(MUTATE, d.effect());
    }
  }

  @Nested
  class Approvals {

    @Test
    void highRiskNeedsAValidApprovalFromSomeoneElse() {
      PolicyEngine enforced = engine(true);
      PolicyDecision none = decide(enforced, ALICE, "packs:write", PACK);
      assertEquals(Outcome.REQUIRES_APPROVAL, none.outcome());
      assertTrue(none.reasons().contains(ReasonCode.APPROVAL_REQUIRED));
      assertEquals("approvals:grant", none.approval().orElseThrow().approverScope());

      Approval ok = approval("bob", NOW.minusSeconds(60), NOW.plusSeconds(600));
      PolicyDecision approved =
          enforced.decide(new PolicyRequest(ALICE, "packs:write", PACK, Map.of(), Optional.of(ok)));
      assertTrue(approved.allowed());
      assertTrue(approved.reasons().contains(ReasonCode.APPROVED));
    }

    @Test
    void expiredSelfMismatchedAndOverlongApprovalsAreRejected() {
      PolicyEngine enforced = engine(true);
      assertRejected(
          enforced,
          approval("bob", NOW.minusSeconds(3000), NOW.minusSeconds(1)),
          ReasonCode.APPROVAL_EXPIRED);
      assertRejected(
          enforced,
          approval("bob", NOW.plusSeconds(10), NOW.plusSeconds(600)),
          ReasonCode.APPROVAL_EXPIRED);
      assertRejected(
          enforced,
          approval("alice", NOW.minusSeconds(60), NOW.plusSeconds(600)),
          ReasonCode.APPROVAL_SELF);
      assertRejected(
          enforced,
          approval("bob", NOW.minusSeconds(60), NOW.plusSeconds(7200)),
          ReasonCode.APPROVAL_TOO_LONG);
      Approval otherPack =
          new Approval(
              "ap-2",
              "bob",
              "packs:write",
              Resource.of("pack", "acme").named("other"),
              NOW.minusSeconds(60),
              NOW.plusSeconds(600));
      assertRejected(enforced, otherPack, ReasonCode.APPROVAL_MISMATCH);
      Approval otherTenant =
          new Approval(
              "ap-3",
              "bob",
              "packs:write",
              Resource.of("pack", "globex").named("commerce-pack"),
              NOW.minusSeconds(60),
              NOW.plusSeconds(600));
      assertRejected(enforced, otherTenant, ReasonCode.APPROVAL_MISMATCH);
    }

    private void assertRejected(PolicyEngine engine, Approval approval, ReasonCode reason) {
      PolicyDecision d =
          engine.decide(
              new PolicyRequest(ALICE, "packs:write", PACK, Map.of(), Optional.of(approval)));
      assertEquals(Outcome.REQUIRES_APPROVAL, d.outcome());
      assertTrue(d.reasons().contains(reason), d.reasons()::toString);
    }

    @Test
    void whenNotEnforcedTheRequirementIsReportedNotHidden() {
      PolicyDecision d = decide(engine(false), ALICE, "packs:write", PACK);
      assertTrue(d.allowed());
      assertFalse(d.approvalsEnforced());
      assertTrue(d.reasons().contains(ReasonCode.APPROVAL_NOT_ENFORCED));
      assertTrue(d.approval().isPresent());
    }

    @Test
    void aRuleCanAskForApprovalOnAnOrdinaryOperation() {
      PolicyRule review =
          new PolicyRule() {
            public String id() {
              return "bulk-review";
            }

            public Verdict evaluate(PolicyRequest r) {
              return Verdict.REQUIRE_APPROVAL;
            }
          };
      PolicyDecision d =
          decide(engine(true, review), ALICE, "catalog:write", Resource.of("index", "acme"));
      assertEquals(Outcome.REQUIRES_APPROVAL, d.outcome());
      assertTrue(d.reasons().contains(ReasonCode.POLICY_REVIEW));
    }
  }

  @Test
  void readOperationsCannotBeHighRisk() {
    assertThrows(IllegalArgumentException.class, () -> new OperationSpec("x", READ, HIGH_RISK));
  }
}

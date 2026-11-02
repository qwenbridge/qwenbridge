# ADR 0023: One Central Policy Engine, Default Deny and Typed Approvals

## Status

Accepted (QwenBridge 1.0, Section 1 Phase 11: QB-071 – QB-077)

## Context

Authorization was spread across the code:

- 37 call sites checked a role string (`Principal.require("search")`).
- Domain Pack policies ran on only two paths. With no pack applying, they
  returned ALLOW with the trace "verified-principal" (audit S2).
- Plugin invocation and connector execution had no policy check.
- High-risk operations had no approval concept. There was a boolean in one
  gateway, and no classification of operations by effect.
- Requests without a token became tenant `public` with role `search` in every
  profile (audit S3).

## Decision

1. **Typed contract (QB-071).** `core.policy` defines the contract:

   | Type | Holds |
   |---|---|
   | `Subject` | id, tenant, scopes, authenticated |
   | `Resource` | type, tenant, domain, connector, id |
   | `OperationSpec` | effect, sensitivity |
   | `PolicyRequest` | subject, operation, resource, context, optional `Approval` |
   | `PolicyDecision` | `ALLOW`, `DENY` or `REQUIRES_APPROVAL`, stable `ReasonCode`s, contributing policy ids, the `ApprovalRequirement` |

   A `PolicyRule` (for example a Domain Pack) can return only
   `NOT_APPLICABLE`, `DENY` or `REQUIRE_APPROVAL`. There is no ALLOW verdict to
   return, so a rule can never grant access.
2. **Default deny (QB-072).** `PolicyEngine` checks in a fixed order and denies
   at the first failure:
   1. The operation must be in the `OperationCatalog`.
   2. SENSITIVE and HIGH_RISK operations need an authenticated subject.
   3. The resource tenant must equal the subject tenant.
   4. The subject must hold a covering scope.
   5. Rules then run; a rule that throws denies.
3. **Scopes (QB-073).** A `Scope` names one operation in one tenant. It can be
   narrowed by domain, connector or resource type. Wildcard operations and
   tenants are rejected, so no scope can cross tenants. The server maps each
   configured role to a scope in the principal's tenant.
4. **Effect classification (QB-074).** Every server operation is classified as
   READ or MUTATE, and as NORMAL, SENSITIVE or HIGH_RISK, in
   `Authorization.CATALOG`. HIGH_RISK operations must be mutating.

   | Operation | Effect | Sensitivity |
   |---|---|---|
   | `search` | READ | NORMAL |
   | `packs:read`, `plugins:read`, `analytics:read`, `evidence:read`, `debug:read` | READ | SENSITIVE |
   | `catalog:write`, `schema:write`, `evidence:write`, `approvals:grant` | MUTATE | SENSITIVE |
   | `packs:write`, `license:write`, `marketplace:publish` | MUTATE | HIGH_RISK |
5. **Approvals (QB-075).**
   - HIGH_RISK operations, and requests a rule flags, need an `Approval`. It
     must cover the same operation, resource and tenant, be current, come from
     a different subject, and be no longer-lived than
     `qwenbridge.policy.approval-max-validity` (default 1h).
   - A principal with `approvals:grant` issues one via
     `POST /api/v2/platform/approvals`. The client sends its id in the
     `X-QwenBridge-Approval` header. Approvals are single-use and kept in
     memory.
   - Enforcement is behind `qwenbridge.policy.approvals-enforced`, which
     defaults to false (user decision). When it is off, the request is allowed
     with `APPROVAL_NOT_ENFORCED` and the decision is logged.
6. **No bypass (QB-076).** `CommerceIdentity.Principal.require` delegates to
   `Authorization`, the server's single enforcement point, so all 37 existing
   call sites now pass through the Core engine.
   - Connector execution (`CommerceBackends.search`) is authorized against the
     plan's tenant and the connector id.
   - `PluginRegistry.invoke` requires a caller and is authorized with the
     plugin as the connector. The caller-less overload was removed.
   - Domain Packs join the engine as a restrict-only rule.
   - Before startup configuration, or after shutdown, a default engine
     applies. It is never a pass-through.
7. **Audit findings S2 and S3.**
   - `PackPolicies` denies an operation the caller holds no scope for. When no
     pack applies, it reports the base scope decision instead of
     "verified-principal". Packs only restrict (user decision).
   - The `%prod` profile requires authentication, and startup fails without
     configured principals. Dev, test and the Docker verification profile keep
     anonymous search and log a warning (user decision). The anonymous subject
     cannot be configured as a credential.
8. **Tests (QB-077).** Tests cover:
   - default deny and unknown operations;
   - anonymous limits;
   - tenant isolation for scopes, connectors and plugins;
   - rules that cannot grant, and failing rules;
   - expired, future, self-issued, mismatched, cross-tenant, overlong and
     reused approvals;
   - over HTTP: enforced approvals and approver-only issuing.

## Consequences

- Adding an operation means classifying it in the catalog. An unclassified
  operation is denied.
- Approvals currently bind an operation to a resource *type* in the tenant,
  because existing call sites check by type. Binding them to a named pack or
  listing follows when those endpoints pass named resources.
- With approvals enforced, platform administration needs a second person.
  Approvals do not survive a restart.

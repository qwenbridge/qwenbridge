# ADR 0049: Policy Pack Contract and Evaluation Order

## Status

Accepted (QwenBridge 1.0, Section 4 Phase 35: QB-249 – QB-253)

## Context

Signed tenant Policy Packs (`PolicyPack`) already ran inside the central policy engine. The most
restrictive result won, and a failing pack denied. However:
- a pack could not say which operations it governed, so it ran for every operation in its
  domains;
- there was no evaluation contract version;
- invalid declarations were not checked;
- the authorization log did not name the pack versions behind a decision.

## Decision

1. **Contract.** `PolicyPack` gains `contract()` (runtime contract 1) and `operations()` (default
   `*`). `ManagedPacks` refuses, at install and activation:
   - an unsupported contract;
   - an operation outside the central catalog;
   - empty or invalid domains;
   - an order outside -10000..10000.
2. **Fixed evaluation order:** Core checks, then tenant Policy Packs by `(order, id)`, then
   Domain Pack rules, then approvals.
   - **Combining:** DENY beats REVIEW, which beats ALLOW.
   - **Fail-closed:** errors deny.
   - **No broadening:** ALLOW never broadens.
   - **Approvals:** an approval never overrides a DENY.

   The full order is in `docs/security/policy-packs.md`.
3. **Traceability.** Traces carry `id@version`, `Outcome.versions()` lists the versions that took
   part, and denials or reviews are logged with them. Upgrades keep earlier versions for rollback.

## Consequences

- **Existing packs keep working.** They get the defaults (contract 1, all operations).
- **Pack changes need approval.** Because `packs:write` is HIGH_RISK, enabling, disabling or
  upgrading a pack needs an approval when approvals are enforced.
- **Not a code sandbox.** Policy Packs are trusted, signed code in the server JVM (see
  `docs/security/plugin-isolation.md`). The engine contains their decisions; it does not contain
  their code.

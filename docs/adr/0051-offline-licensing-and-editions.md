# ADR 0051: Offline Licensing, Entitlements and Editions

## Status

Accepted (QwenBridge 1.0, Section 4 Phases 37 and 38: QB-261 – QB-273)

## Context

`Entitlements` already verified Ed25519-signed licenses offline and gated features through a
capability matrix. However:
- every refusal was one generic error;
- there was no grace policy;
- configured keys were not checked to be public keys;
- pack gating was spread across `ManagedPacks`;
- a downgrade did not report its effect on active packs.

## Decision

1. **Typed license validation.** Version 1 only; `Problem` codes; Community fallback with the
   reason in `Status` (`state`, `problem`). Community capabilities are the baseline of every
   license.
2. **Keys.** Only Ed25519 public keys are accepted, for licenses and pack publishers, checked at
   startup. Rotation works by overlapping trusted keys.
3. **Grace.** `qwenbridge.licensing.grace-period` (default none, at most 90 days) keeps an expired
   license's entitlements, reported as `GRACE`. Expiry and downgrades never delete state.
4. **Pack licensing in one place.** `ManagedPacks.requiredFeatures` derives a pack's needs from
   whether it is bundled and its kind, never from manifest metadata. `suspended(tenant)` lists
   packs the current license no longer covers, and the license endpoints return them.
5. **Edition matrix.** Documented in `docs/editions.md` as a proposal for the product owner.
   `web` is the only ungated capability, and a test checks that every other capability has a
   server-side check.

## Consequences

- **No license server.** Offline operation needs none, and Community needs no license.
- **Documented limits.** Signatures protect license integrity, not a self-hosted binary; offline
  validity depends on the server clock.
- **SDKs.** SDKs see entitlement refusals as the standard `api-error/2` 403 body. There is no
  SDK-specific licensing API in 1.0.

# ADR 0052: Pack Distribution, Trust and Compatibility

## Status

Accepted (QwenBridge 1.0, Section 4 Phase 39, part 1: QB-274 – QB-279)

## Context

The pieces already existed: signed pack artifacts, a trusted-publisher map, an immutable,
publisher-owned local marketplace, and install-time validation. However:
- there was no file format or tooling to produce and move packages offline;
- there was no signed release metadata;
- there was no revocation;
- compatibility rules were spread across several ADRs.

## Decision

1. **Package format.** `qwenbridge-pack/1`, a flat JSON object holding the signed pack and
   optional signed metadata. The format is reproducible.
2. **Publisher identity is the signing key.** Release metadata is signed by the same key over the
   JAR digest. It is shown with installed packs and marketplace listings.
3. **Offline tooling.** `PackTool` in the plugin SDK, run through `scripts/packs/qbpack.sh`:
   `keygen`, `pack`, `verify`, `inspect` and `request`. It uses the JDK only and keeps private keys
   with the publisher.
4. **Revocation.** `qwenbridge.packs.revoked-keys` and `qwenbridge.packs.revoked-digests` are
   checked first, at inspection and every load. Revoked packs stay listed but cannot be used.
5. **Compatibility rules and the guarantee policy.** Collected in
   `docs/architecture/pack-distribution.md`.
6. **Release-candidate checklist.** Every gate has an owner or process, a method and a recorded
   outcome (`docs/release/QwenBridge-1.0-RC-Checklist.md`).

## Consequences

- **Offline end to end.** A trusted pack can be packaged, transferred and installed without
  network access (`PackDistributionTest`).
- **Trust changes need a restart.** There is no online revocation list in 1.0.
- **Signing in the build is not included.** Publishers run the tool themselves; CI signing is
  future work.

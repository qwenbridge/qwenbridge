# ADR 0050: Local Pack Registry and Lifecycle

## Status

Accepted (QwenBridge 1.0, Section 4 Phase 36: QB-254 – QB-260)

## Context

`ManagedPacks` already provided:
- signed installation;
- per-tenant activation with dependency and cycle checks;
- immutable versions and history-based rollback.

Its state lived in the platform store, which was either in memory (lost on restart) or Redis.
Several things were missing:
- an offline deployment had no persistent registry;
- packs could not state which runtime they support;
- two Domain Packs could claim the same domain;
- rollback did not say what it restores.

## Decision

1. **File store.** `qwenbridge.platform.store=file` with `qwenbridge.platform.file-dir`:
   - one JSON file per entry, written to a temporary file and renamed atomically;
   - serialized within the process;
   - TTLs honoured;
   - for one instance. Commerce evidence and analytics stay in process in this mode.
2. **Runtime range.** `PackManifest.runtime` (optional, `minimum` inclusive, `maximumExclusive`)
   is checked against `qwenbridge.version` during metadata inspection, before any pack code
   runs. Manifests without it are unaffected.
3. **Capability conflicts.** Activating a Domain Pack whose domain another enabled Domain Pack
   already provides is refused.
4. **Rollback report.** `rollback` returns `from`, `to`, `configurationRestored` and the list of
   effects it does not revert. A rollback that would no longer validate is refused, and the state
   does not change.
5. **Documented installation contract.** Inspect without code execution, install with signed code
   verification but no start, then a separate activation.

## Consequences

- **Offline single-instance deployments.** They keep packs and licenses across restarts with no
  database, Redis or marketplace. Several instances still need Redis.
- **What is checked.** Dependency versions and cycles, Domain Pack domains, SDK API version and
  runtime range. Connector Packs (ADR 0047) are configured per deployment and are not part of the
  tenant registry.
- **Rollback is configuration-level.** It is not a transaction over side effects; the limits are
  reported with every rollback.

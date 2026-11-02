# Local Pack Registry and Lifecycle

`ManagedPacks` keeps each tenant's pack registry in the platform store. It needs no hosted
registry, marketplace or network access. ADR 0050 records the decisions.

## Storage (QB-254, QB-259)

| `qwenbridge.platform.store` | Where state lives | Use |
|---|---|---|
| `memory` | In process; lost on restart | Tests, demos |
| `file` | One JSON file per entry in `qwenbridge.platform.file-dir`, written atomically | Single-instance deployments with no database |
| `redis` | Redis, compare-and-set | Several instances sharing state |

With `file` (and `memory`), Commerce evidence and analytics are kept in process, so no Redis is
needed. Instances must not share a file directory.

**What the registry holds per tenant.** For every installed version:
- the manifest: identity, version, kind, publisher, dependencies, capabilities, configuration
  fields and runtime range;
- the artifact digest and the signed artifact.

It also holds the selected version per pack, the enabled packs, the version history and each
version's configuration. `GET /api/v2/platform/packs` lists it.

## Installation contract (QB-255)

Installation and activation are separate steps.

1. **Inspect** (metadata only; no pack code runs):
   - size limits (manifest 64 KiB, JAR 2 MiB, expanded 8 MiB, 1,000 entries);
   - the signature by a trusted publisher (`qwenbridge.packs.trusted-publishers`) over manifest and
     JAR;
   - the manifest is valid for pack API 1;
   - the publisher matches the signing key;
   - the runtime range accepts this server (`qwenbridge.version`).
2. **Install** (`packs:write`; the `external-packs` entitlement):
   - the signed code is loaded once to check that it declares exactly one pack whose manifest
     equals the signed one and whose kind matches;
   - Policy Packs are also checked (ADR 0049);
   - the pack is not started;
   - installed versions are immutable: the same id and version with other content is refused,
     while the same artifact again is accepted.
3. **Activate:** see below.

A refused package leaves no trace in the registry.

## Activation, upgrade and disable (QB-256, QB-257)

Before the active state changes, `activate` checks, in order:
1. the version is installed;
2. entitlements (`external-packs`; `tenant-policy` for Policy Packs);
3. the configuration against the manifest's fields, including that secret references resolve;
4. dependencies:
   - every enabled pack's dependencies are enabled in an accepted version range;
   - no cycles (a cycle can never be activated);
5. that no other enabled Domain Pack provides the same domain;
6. that the pack loads and starts.

**Failure:** if any check fails, the state is unchanged and the reason is returned. A concurrent
change is refused with 409.

**Repeatable and upgrades:** activating the active version again is accepted. Activating another
version is an upgrade, and the previous version is kept in the history (up to 8).

**Disable:** refused while an enabled pack depends on the pack.

## Rollback (QB-258)

`POST /api/v2/platform/packs/{id}/rollback` reactivates the previous version with the
configuration recorded for it. It passes through every activation check.

**When it can't be done:** there may be no earlier version, or the earlier version may no longer
validate (for example a dependency was disabled since). The rollback is then refused with
"Rollback of … is not supported: …" and nothing changes.

**Response:** `from`, `to`, `configurationRestored`, and `notReverted`. Rollback does not undo:
- data or external state the pack changed while it was active;
- tenant schema versions registered meanwhile;
- the newer version's installation (installed versions are immutable).

It is not a transaction over a plugin's side effects.

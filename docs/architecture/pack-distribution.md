# Pack Distribution, Trust and Compatibility

Packs are distributed as signed files and installed offline. No storefront, payment system,
publisher portal or online marketplace is needed. ADR 0052 records the decisions.

## The package (QB-274, QB-275)

A `.qbpack` file is one JSON object of string fields (format `qwenbridge-pack/1`):

| Field | Content |
|---|---|
| `format` | `qwenbridge-pack/1` |
| `keyId` | The publisher key that signed the package; the publisher's identity |
| `digest` | SHA-256 of the JAR (hex) |
| `manifest` | The pack manifest (JSON text) |
| `jar` | The pack JAR (Base64) |
| `signature` | Ed25519 over `qwenbridge-pack-v1\n<manifest>\n<digest>` |
| `metadata` | Optional release information: `description`, `documentation` (an https URL), `contact`, `releaseNotes` |
| `metadataSignature` | Ed25519 over `qwenbridge-pack-metadata-v1\n<digest>\n<metadata>`, by the same key |

**What the manifest gives.** The manifest (`PackManifest`) carries:
- identity, version, kind and publisher;
- the pack API version, dependencies with version ranges, capabilities and configuration fields;
- the optional runtime range.

That is everything needed to inspect and validate a pack without running it or contacting a
marketplace.

**Publisher identity is the signing key, not a name.**
- The manifest's `publisher` must equal the key id.
- The server trusts only keys configured in `qwenbridge.packs.trusted-publishers`.
- Metadata cannot name another publisher: it is signed by the same key and bound to the digest,
  so it cannot be altered or moved to another package.

## Workflow (QB-278)

`scripts/packs/qbpack.sh` runs the plugin SDK's `PackTool`. It needs the JDK only; build the SDK
first with `mvn -q -pl qwenbridge-plugin-sdk -DskipTests package`.

| Step | Where | Command |
|---|---|---|
| 1. Create a publisher key (once) | Publisher | `qbpack.sh keygen publisher.key publisher.pub` |
| 2. Package and sign | Publisher | `qbpack.sh pack manifest.json pack.jar <key-id> publisher.key pack.qbpack [metadata.json]` |
| 3. Transfer | — | Copy `pack.qbpack` by any means (file share, removable media) |
| 4. Verify | Receiving site | `qbpack.sh verify pack.qbpack <key-id> publisher.pub` |
| 5. Inspect (no code runs, no key needed) | Receiving site | `qbpack.sh inspect pack.qbpack` |
| 6. Install | Receiving site | `qbpack.sh request pack.qbpack` gives the body for `POST /api/v2/platform/packs` (`packs:write`) |
| 7. Activate | Receiving site | `POST /api/v2/platform/packs/{id}/activate` |

**Keys.** The private key stays with the publisher. The receiving site configures the public key
(`publisher.pub`) as `{"<key-id>": "<public key>"}` in `qwenbridge.packs.trusted-publishers`.

**Reproducible.** The same manifest, JAR, metadata and key give byte-identical packages, because
Ed25519 signatures are deterministic.

## Trust store and revocation (QB-276)

**Trust store.** `qwenbridge.packs.trusted-publishers` maps key ids to Ed25519 public keys. Startup
fails if a value is not a public key.

**Checks, in order, before any pack code runs:**
1. size limits;
2. revocation;
3. the package signature;
4. the metadata signature;
5. the manifest, publisher and runtime range.

The server re-runs these checks whenever it loads a pack, so revocation also stops packs that are
already installed.

**Revocation:**

| Setting | Effect |
|---|---|
| `qwenbridge.packs.revoked-keys` | Refuses every package by those publisher keys |
| `qwenbridge.packs.revoked-digests` | Refuses those exact packages |

Revoked packs stay listed but cannot be used. Nothing is deleted. Bundled packs are not affected.

**Trust updates (offline):**
- **Adding a publisher:** add its public key.
- **Rotating a key:** trust the new key, re-sign the packs, then remove the old key.
- **Compromised key:** add it to `revoked-keys` and remove it from the trust store.

Configuration changes take effect on restart.

## Compatibility rules (QB-277)

Checked before installation or activation:

| Contract | Rule | When |
|---|---|---|
| Pack API (SDK) | `apiVersion` must be 1 | Inspection |
| Runtime (Core) | `runtime` range, when given, must accept `qwenbridge.version` | Inspection |
| Kind and implementation | The loaded pack must match the signed manifest exactly and implement its kind | Installation |
| Policy contract | `PolicyPack.contract()` must be 1; operations must be known | Installation |
| Dependencies | Enabled, within version ranges, no cycles | Activation |
| Domain schema | One provider per domain; schemas owned by `pack:<id>` and immutable per version | Activation |
| Plugin SDK (plugins and agents) | `sdk` and `runtime` ranges in `plugin.json` (ADR 0046) | Discovery |
| Connector SPI (Connector Packs) | `spiMinimum`..`spiMaximumExclusive` must include 1.0.0 (ADR 0047) | Discovery |

**Guarantees and breaking changes.** Within a major version:
- the contracts above only gain optional members with defaults;
- existing packs keep loading;
- a breaking change raises the contract number (pack `apiVersion`, `PolicyPack.CONTRACT`,
  Connector SPI or Plugin SDK major).

Packs for the old contract are then refused with the reason, never loaded against an
incompatible runtime. Migration guidance is published with the release that raises the
contract.

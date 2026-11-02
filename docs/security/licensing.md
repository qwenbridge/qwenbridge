# Licensing and Entitlements

QwenBridge licensing is offline, capability-based and signed. No license server is needed to
start, to validate a license, or to run Community features. ADR 0051 records the decisions.

## License contract (QB-261)

A license is a signed envelope: `{keyId, payload, signature}`.
- **`payload`:** the Base64 of a JSON document, the data.
- **`signature`:** separate from the data.

The document has these fields:

| Field | Meaning |
|---|---|
| `version` | Contract version; only `1` is supported |
| `id` | License identity |
| `issuer` | Must equal the signing `keyId` |
| `tenant` | The tenant (organization) the license is bound to |
| `edition` | `COMMUNITY`, `PROFESSIONAL` or `ENTERPRISE` |
| `notBefore`, `expiresAt` | Validity period |
| `features` | Granted capabilities; must be within the edition's matrix |

Validation runs before any use and reports one typed problem:
- `MALFORMED`, `UNKNOWN_KEY`, `BAD_SIGNATURE`, `UNSUPPORTED_VERSION`;
- `INCOMPLETE`, `WRONG_TENANT`, `ISSUER_MISMATCH`, `FEATURES_EXCEED_EDITION`;
- `NOT_YET_VALID`, `EXPIRED`.

Messages never repeat license contents.

## Signatures and keys (QB-262)

- **Algorithm:** Ed25519 over the exact payload bytes. The bytes are not re-serialized, so the
  signed representation is canonical by construction.
- **Keys:** the server holds only public keys (`qwenbridge.licensing.trusted-keys`, key id to
  Base64 X.509 key). A configured value that is not an Ed25519 public key (for example a private
  key pasted by mistake) stops startup without echoing it. Private signing keys stay with the
  issuer and are never distributed.
- **Rotation:**
  1. Trust the new key alongside the old one.
  2. Issue licenses with the new key.
  3. Remove the old key.

  Licenses signed by a removed key stop granting, reported as `UNKNOWN_KEY`. Pack publisher keys
  (`qwenbridge.packs.trusted-publishers`) follow the same rules.

## Entitlements (QB-263, QB-264)

**Central checks.** Gated operations call `Entitlements.require(tenant, capability)`. Edition
names are never checked in feature code.

**Not a permission.** An entitlement does not grant access: every gated operation still needs the
caller's permission, such as `packs:write`, `marketplace:publish` or `license:write`.

**Pack licensing** is decided by the server from facts the server controls, never from a pack's
manifest:

| Pack | Needs |
|---|---|
| Bundled with the application (Commerce, Library) | `builtin-packs` (every edition) |
| Any other pack, whatever its id or declared capabilities | `external-packs` |
| Policy Packs, in addition | `tenant-policy` |

## Expiry and grace (QB-265)

| Situation | Result |
|---|---|
| Before `notBefore` | Not in effect (`NOT_YET_VALID`); cannot be installed |
| Before `expiresAt` | `VALID` |
| After `expiresAt`, within `qwenbridge.licensing.grace-period` (default `PT0S`, at most 90 days) | `GRACE`: entitlements kept, `EXPIRED` reported |
| After that | `INVALID`: Community entitlements |

Expiry and downgrades are never destructive:
- the stored license, installed packs, their configuration and tenant data are kept;
- packs the license no longer covers are suspended (they refuse use, reported in
  `suspendedPacks`);
- they resume when a valid license is installed.

## Limits of these protections (QB-266)

- **Integrity, not secrecy.** Signatures detect any change to license data and any license not
  issued with a trusted key. They do not stop someone who controls the server from changing the
  server itself, for example by patching the binary or adding a trusted key. Licensing deters
  and records; it is not a technical barrier against the operator of their own deployment.
- **Offline clocks.** Validity is checked against the server's clock, which an operator can set
  back to extend an expired license. Without a network time source this cannot be prevented
  offline. Deployments that need it must keep the clock trusted (NTP and monitoring).

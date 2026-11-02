# Editions: Community, Professional and Enterprise

> **Status: proposal.** The capability split below is how the code is configured
> (`Entitlements.MATRIX`). Which capabilities belong to which commercial edition is a product
> decision for the owner to confirm. Changing it is a one-line change to the matrix; the
> enforcement does not change.

All editions run the same Core and server. An edition is a set of capability names granted by a
license (see [security/licensing.md](security/licensing.md)). Without a license, a deployment is
Community.

| Capability | Community | Professional | Enterprise | Enforced at | Also needs permission |
|---|:-:|:-:|:-:|---|---|
| `search` (OpenSearch and in-process backends) | ✓ | ✓ | ✓ | Commerce search, backend selection | `search` |
| `sessions` | ✓ | ✓ | ✓ | Commerce sessions | `search` |
| `relaxation` | ✓ | ✓ | ✓ | Zero-result recovery | `search` |
| `playground` | ✓ | ✓ | ✓ | Playground endpoints | `debug:read` |
| `builtin-packs` (Commerce, Library) | ✓ | ✓ | ✓ | Pack activation and use | `packs:write` to change |
| `marketplace-read` | ✓ | ✓ | ✓ | Marketplace list and download | `search` |
| `web` (browser component) | ✓ | ✓ | ✓ | Not gated: a static asset | — |
| `external-packs` | | ✓ | ✓ | Pack install, activation and use | `packs:write` |
| `tenant-policy` (Policy Packs) | | | ✓ | Policy Pack activation and use | `packs:write` |
| `marketplace-publish` | | | ✓ | Marketplace publish | `marketplace:publish` and the publisher scope |

**Separate from editions.** Operational permissions (scopes on API principals) and deployment
settings (for example Connector Packs, `qwenbridge.connector-packs.*`) are not edition matters.

**Community baseline (QB-269).** Community covers:
- the Universal Core;
- local retrieval (keyword, vector and hybrid on the in-process index, and OpenSearch);
- multilingual analysis and the built-in packs.

It needs no license and no license server.

**Edition changes (QB-272):**
- **Upgrade:** install a license; no reinstallation is needed.
- **Downgrade or expiry:** packs the new edition does not cover are suspended, kept and listed
  in `suspendedPacks`. Nothing is deleted.

## Tests

| Test | Covers |
|---|---|
| `LicensingAndEditionsTest` | The rules in this table: typed refusals, key rotation, grace, pack gating per edition, downgrade and upgrade, and that every gated capability has a server-side check |
| `OfflinePackRegistryApiTest` | A server with no license reports Community |

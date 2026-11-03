# QwenBridge 1.0: Compatibility Matrix

**Code versions.** Server, Java SDK and TypeScript SDK are built as `1.0.0-RC1`. The
`1.0` contract versions below are what they implement.

**How support is proven.** Every "supported" row below is checked by the master verification run
(`scripts/qwenbridge-final-verification.sh`) through the named tests or steps.

## Contracts

| Contract | Version | Status | Executable check |
|---|---|---|---|
| REST API v2 | `/api/v2`, error body `api-error/2` | Supported (1.0) | `ApiEndpointSnapshotTest` (reviewed endpoint list), API tests per area |
| REST API v1 | `/api/v1` | Supported, maintained; refused with `409 TENANT_API_REQUIRED` in authenticated deployments (except health and version) | `SearchAnalyzeControllerTest`, security tests |
| SSE progress events | `progress-event/2` | Supported | SSE streaming tests, SDK streaming tests |
| Commerce search | `commerce-search/1.0` | Supported | `CommerceApiTest`, SDK contract fixtures |
| QueryIR | `1.0` (domain payloads under `extensions.<domain>`, versioned per pack) | Supported | `QueryIrJson` tests, `DomainPackLifecycleTest` |
| Connector configuration | `connector-config/1` | Supported | `ConnectorTest`, `ConnectorPacksTest` |
| Connector SPI | `1.0.0` (packs declare a range) | Supported | `ConnectorPacksTest`, `ConnectorPackOutageTest`, `PacksApiTest` (test-only `ExampleConnectorPack`) |
| Plugin SDK | `1.0.0` (`plugin.json`, `manifestVersion` 1) | Supported | `PluginHostTest`, `AgentAuthorizationTest` |
| Legacy `QwenBridgePlugin` interface | — | Supported, unchanged; new plugins should use Plugin SDK 1.0 | `PluginExternalJarTest` |
| Pack API | `apiVersion` 1 (`PackManifest`, optional `runtime` range) | Supported | `PackLifecycleTest`, `PackAcceptanceTest` |
| Policy Pack evaluation contract | `PolicyPack.CONTRACT` 1 | Supported | `PolicyPackEnforcementTest` |
| Pack package format | `qwenbridge-pack/1` | Supported | `PackDistributionTest` |
| License document | `version` 1, Ed25519 | Supported | `LicensingAndEditionsTest` |

## Clients

| Client | Against | Check |
|---|---|---|
| Java SDK 1.0.0-RC1 (v2 Commerce client, v1 client) | This server | Unit tests (76); live v2 calls and live analyze in the master run; `ServerContractCompatibilityTest`; `ApiContractFixturesTest` (fixtures captured from the server) |
| TypeScript SDK 1.0.0-RC1 | This server | Unit tests (70); live v2 calls on Node.js 20 in the master run |
| Web component | This server | Browser tests (12, Chromium) |

## Removed in QB-287 (breaking)

| Change | Before | After | Migration |
|---|---|---|---|
| Connector backends | `qwenbridge.commerce.backend` accepted `elasticsearch`, `postgresql`, `mongodb` (connector modules) | Only `memory` (default) and `opensearch`; the others give "Unknown backend" | Use `memory` or `opensearch`, or implement the source as a connector pack (`ConnectorPackProvider`) |
| Edition features | `elasticsearch`, `postgresql`, `mongodb` in the edition matrix | Removed; `opensearch` and `memory` are part of `search` | **A licence that lists any of them is refused** (`FEATURES_EXCEED_EDITION`), because its features exceed the edition matrix. Reissue the licence without them |
| Connector environment | `QWENBRIDGE_CONNECTORS_*` variables | Ignored | Remove them |
| Shopify | Test-scope connector, sandbox test in the server | Isolated E2E check (`e2e/dual-sdk/provider-adapters/shopify_check.py`) | None for clients |
| Catalog sync without Redis | Untyped failure | `503 DEPENDENCY_UNAVAILABLE` "Catalog synchronization needs Redis for its checkpoint and lock" | Run Redis for sync, or load static catalogs with `qwenbridge.demo.dataset-files` |
| Readiness | Listed `opensearch` even when not selected | Lists it only when `qwenbridge.search.backend` or `qwenbridge.commerce.backend` is `opensearch` | Monitoring that expected the entry must check the selected backend |
| Compose | The application waited for a healthy Redis | Starts without Redis (`required: false`) | None |
| `examples/` | Java and TypeScript example applications | Removed; see `e2e/dual-sdk` consumers and the README quick starts | — |

The REST, SSE and SDK contracts are unchanged. ADR 0058 and the
[cleanup report](QwenBridge-1.0-Cleanup-Report.md) have the details.

## Not supported

- **Error bodies.** The v1 error body keeps its seven fields unchanged. v2 errors use
  `api-error/2`, which adds `category` and `retryable`. No earlier v2 error format is supported.
- **Packs written for another Pack API, Policy contract, Connector SPI or Plugin SDK major
  version.** They are refused with the reason at inspection or discovery. They are never loaded
  against an incompatible runtime.
- **Licenses of version 2 or later.** They are refused as `UNSUPPORTED_VERSION`.
- **SDK 0.x clients older than this branch.** They are not tested. Use the 0.9.1 SDKs.

## Migration paths

| From | To |
|---|---|
| v1 analyze | v2 search (`docs/api/rest-api.md`) |
| Legacy plugin jars | Plugin SDK 1.0 manifests (`docs/security/plugin-isolation.md`, ADR 0046) |
| Unsigned pack artifacts | `qbpack` packages (`docs/architecture/pack-distribution.md`) |

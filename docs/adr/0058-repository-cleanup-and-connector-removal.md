# ADR 0058: Repository Cleanup and Connector Removal

## Status

Accepted (QB-287).

It supersedes, in part:
- ADR 0038 (Elasticsearch connector);
- ADR 0039 (PostgreSQL and MongoDB connector modules);
- the connector-module parts of ADRs 0028, 0029, 0030, 0031, 0043, 0047 and 0053.

Each of those ADRs carries a supersession note.

## Context

**The 0.9.1 tree before QB-287:**
- **Connector modules:** four optional modules (`qwenbridge-connector-elastic`, `-mongodb`,
  `-postgresql`, `-shopify`). None was needed by the default, database-free runtime. The Shopify
  connector was a test-scope dependency of the server.
- **Examples:** two `examples/` applications duplicated what the dual-SDK E2E suite (ADR 0057)
  now covers.
- **Runtime:** OpenSearch was probed by readiness even when not selected. Redis gated the
  application's start in Compose.
- **Dead code:** the server contained orphaned v1 per-step AI services and unused types.

## Decision

1. **Remove the four connector modules completely:**
   - sources, POM modules and dependencies;
   - Dockerfile lines and the table-1 Compose file;
   - the table-1 verification scripts;
   - CI install lines, and tests that only validated them.

   **What is kept:**
   - The neutral filter semantics, recovered as `connectors/NeutralFilterConformance`.
   - The OpenSearch filter translation (`connectors/OpenSearchFilters`, package-private).

   The backend identifiers `elasticsearch`, `postgresql` and `mongodb`, and their edition
   features, no longer exist. New data sources arrive as connector packs (`ConnectorPackProvider`),
   not as server modules. A test-only `ExampleConnectorPack` keeps the SPI covered.
2. **Keep OpenSearch, strictly optional.**
   - It is a selectable backend (`opensearch`), off by default.
   - It is probed only when selected (`DependencyHealthChecker.active()`).
   - It is behind the Compose profile `opensearch`.
   - The remaining couplings and an extraction plan are in
     [remediation proposal 1](../architecture/remediation-proposals.md#1-extract-opensearch-into-an-optional-connector-pack).
3. **Keep the v1 API** (`/api/v1`) and the SDKs' v1 clients for backward compatibility. Three v1
   scenarios per SDK in the E2E suite replace the examples' coverage. The legacy pipeline and a
   migration onto the Core engine are documented
   ([proposal 2](../architecture/remediation-proposals.md#2-serve-the-v1-api-from-the-core-engine)).
4. **Remove `examples/`.**
   - The E2E consumers are the runnable examples.
   - The README keeps minimal Java and TypeScript quick starts.
5. **Redis is never mandatory.**
   - Compose starts the application without it (`required: false`).
   - Every cache and rate limit has an in-memory fallback.
   - Catalog synchronization keeps Redis, as its checkpoint and lock store. Without Redis it
     answers with a typed 503 `DEPENDENCY_UNAVAILABLE` instead of an untyped failure.
   - A Redis-free design is
     [proposal 3](../architecture/remediation-proposals.md#3-catalog-synchronization-without-redis).
6. **Keep the Library pack** as the second, independent domain, with its own tests
   (`LibraryPackTest`) and a server pipeline test (`LibraryDomainPipelineTest`).
7. **Commerce:** the target separation is documented, with no migration
   ([proposal 4](../architecture/remediation-proposals.md#4-separate-commerce-from-the-server)).
8. **Remove dead code** only when no reference exists, including CDI producers, observers and
   filters. One wrongly removed producer (`LatencyHistograms`) was restored after the full test
   run caught it.

## Consequences

- **Smaller reactor:** 7 Maven reactor modules (with the parent) instead of 12. The 5 removed are
  the 4 connector modules and `examples/java-sdk-example`.
- **Breaking changes**, listed in the
  [cleanup report](../release/QwenBridge-1.0-Cleanup-Report.md):
  - the removed backend identifiers and edition features;
  - the catalog-sync 503 without Redis;
  - readiness no longer lists an unselected OpenSearch.
- **New tests:** `ArchitectureNeutralityTest` fails if a connector module, Shopify code or a
  mandatory database or search client returns, or if the application waits for Redis.
- **New documentation:** architecture overview, class diagrams, flows, sequence diagrams and
  remediation proposals under `docs/architecture/`.

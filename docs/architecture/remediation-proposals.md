# Remediation Proposals

**Status:** proposals only. Nothing here is implemented.

QB-287 removed the connector modules and examples, and made OpenSearch and Redis optional at
startup. Four architectural couplings remain. Removing each would be a high-risk change, so each
gets a concrete plan here instead of an implementation:
- affected modules;
- behaviour changes;
- migration steps;
- acceptance criteria.

Inventories were taken from the source on 2026-11-02. Counts are of `.java` files under
`qwenbridge-server/src/main/java/io/qwenbridge`; lines are non-blank, non-comment lines.

| # | Proposal | Risk | Size | Prerequisite |
|---|---|---|---|---|
| 1 | Extract OpenSearch into an optional connector pack | High | 27 server files | — |
| 2 | Serve the v1 API from the Core engine | High | about 120 server files on the v1 path | 1 helps, not required |
| 3 | Catalog synchronization without Redis | Medium | 1 class plus an SPI | — |
| 4 | Separate Commerce from the server | High | `commerce` 23 files (5,580 lines), `connectors` 8 (912) | 1 |

## 1. Extract OpenSearch into an optional connector pack

**Today.**
- **Startup:** OpenSearch is optional (QB-293, QB-287). The default backends are `memory` and
  `in-memory`. The health checker probes OpenSearch only when it is selected. The Compose service
  sits behind `--profile opensearch`.
- **Code:** the OpenSearch implementation is still compiled into the server. The REST client
  configuration `quarkus.rest-client.opensearch` is always present.
- **Tests:** the `%test` profile selects `opensearch` for both backends. The existing Commerce
  suites were written against that path, with a stubbed REST client.

**Coupling inventory (27 files):**

| Package | Files | What |
|---|---|---|
| `execution` | 10 | `provider/opensearch/*` (client, auth headers, query factory, response mapper, DTO, properties), `OpenSearchProvider`, `DefaultSearchProviderResolver`, `ExecutionPlanFactory` |
| `connectors` | 5 | `OpenSearchBackend`, `OpenSearchFilters`, `NeutralFilterTranslator.openSearch`, `CommerceBackends`, `ConnectorProviders` |
| `analysis` | 4 | `SearchAnalysis`, its prompt builder and two parsers. The model is asked for OpenSearch-shaped hints |
| `commerce` | 3 | `CommerceSearchService` (injects `OpenSearchProvider` but never uses it), `CommercePlanning`, `CatalogSynchronization` (`OpenSearchApiClient` fallback sink when no `CommerceBackends` is injected) |
| `operations` | 2 | `OpenSearchDependencyHealthChecker`, `OperationsMetrics` |
| `config`, `decision`, `intelligence` | 3 | `QwenBridgeConfigProducer`, `decision.SearchBackend`, `StructuredQueryCoordinator` |

**Affected modules:** `qwenbridge-server`, and a new `qwenbridge-opensearch-pack` that depends
only on `qwenbridge-plugin-sdk`, `qwenbridge-core` and a JDK HTTP client. The core and the SDKs
are unchanged.

**Behaviour changes:**
- **Selection:** `qwenbridge.commerce.backend=opensearch` works only when the pack is installed.
  Otherwise the server answers "Unknown backend", as it does for the removed identifiers.
- **Health:** the OpenSearch readiness check moves into the pack, and the connector's state is
  reported through `ConnectorRuntime`.
- **Prompt:** the v1 analysis prompt no longer asks for OpenSearch-specific hints. This changes the
  v1 `SearchAnalysis` field set; see proposal 2.

**Migration steps:**
1. Remove the unused `OpenSearchProvider` injection from `CommerceSearchService`. Adjust the
   constructor callers in the tests. (Low risk; can be done on its own.)
2. Make `CatalogSynchronization` always write through `CommerceBackends`, and delete the
   `OpenSearchApiClient` fallback sink.
3. Rewrite the Commerce suites under `%test` against `memory`. They then cover the default
   deployment. Keep a separate, tagged OpenSearch suite that runs against the pack.
4. Move `execution/provider/opensearch/*`, `OpenSearchBackend` and `OpenSearchFilters` into the
   new module as a `ConnectorPackProvider` whose `Connector.search()` returns a core
   `SearchProvider`.
5. Register the pack's backend in `CommerceBackends` through `ConnectorPackService`, not by
   injection.
6. Remove `quarkus.rest-client.opensearch`, `OpenSearchDependencyHealthChecker` and the
   `OperationsMetrics` gauges from the server.
7. Neutralize the v1 analysis prompt and parsers. Version the change (`analysis.cache.version`).

**Acceptance criteria:**
- `rtk proxy grep -ril opensearch qwenbridge-server/src/main` returns nothing. The server POM has
  no OpenSearch artifact.
- The full server suite passes with `%test` on `memory`.
- The tagged OpenSearch suite passes against a real OpenSearch with the pack installed
  (`OpenSearchConformanceLiveTest` and its fixtures, moved with the pack).
- Master step 21 (database-free runtime) and the dual-SDK E2E stay green.
- Without the pack, `backend=opensearch` gives a typed "Unknown backend". With the pack and an
  unreachable OpenSearch, the result is a typed `SEARCH_PROVIDER_ERROR`, never a startup failure.

## 2. Serve the v1 API from the Core engine

**Today.** The legacy v1 API runs on its own path:
- `POST /api/v1/search/analyze`, `GET /api/v1/search/stream/{requestId}`;
- a 15-step CDI pipeline (`pipeline` 40 files, 1,640 lines);
- the execution engine (`execution` 36 files, 1,151 lines);
- threat services (`threat` 45 files, 1,260 lines);
- the analysis model (`analysis` 19 files, 1,033 lines);
- `intelligence` (18), `normalization` (10), `model` (16) and `streaming` and `event` (30).

v2 runs on `CommerceSearchService` and the Core. Language, typo correction, expansion and QueryIR
are already shared: the v1 steps `LanguageStep`, `NormalizationStep`, `ExpansionStep` and
`QueryIrStep` call the same services. Planning, validation and retrieval are not shared.

The v1 API is not tenant-aware, so it is refused (409) when authentication is required. QB-287
kept it for backward compatibility. Its contracts are tested by the Java SDK contract fixtures
(`v1-analyze-response.json`, `v1-api-error.json`) and by three E2E scenarios per SDK.

**Affected modules:** `qwenbridge-server` (`api`, `pipeline`, `execution`, `analysis`, `decision`,
`intent`, `semantic`, `streaming`, `event`). `qwenbridge-java-sdk` and `qwenbridge-typescript-sdk`
are affected only through their contract tests, which must keep passing unchanged.

**Behaviour changes:**
- **Unchanged:** v1 responses keep their JSON shape. `intent`, `decision`, `confidence`, `rewrite`
  and the SSE event types and order (`stream.connected` first, one terminal event) stay as they
  are.
- **Changed internally:** v1 retrieval uses the Core planner and `CommerceBackends`. It therefore
  honours hard-constraint verification, the same as v2.
- **Changed values:** some v1 values change, because the Core's deterministic analysis replaces
  per-step heuristics. This is recorded in the compatibility document.

**Migration steps:**
1. Freeze the v1 contract. Record golden responses for the E2E v1 scenarios and the SDK fixtures
   (`target/contract`).
2. Introduce `V1Adapter`. It maps `SearchAnalyzeRequest` to a v2 `SearchRequest` for an internal
   `legacy` tenant, and maps `SearchResponse` back to `SearchAnalyzeResponse`.
3. Emit the v1 SSE events from the v2 stage callbacks, with the v1 event names.
4. Run both paths side by side behind `qwenbridge.v1.engine=pipeline|core`, with a comparison
   test over the golden set.
5. Switch the default to `core` and keep `pipeline` for one release.
6. Delete `pipeline`, `execution`, the v1-only `analysis` and `threat` classes, and their tests.
   Record the counts.

**Acceptance criteria:**
- The SDK contract tests (Java 76, TypeScript 70) pass unchanged.
- The E2E v1 scenarios pass in both consumers with 0 divergent.
- Golden comparison: identical `intent` and SSE event sequence for every scenario. Any `decision`
  or `confidence` difference is listed and approved.
- The server shrinks by at least the `pipeline` and `execution` packages.
- No v1 request reaches OpenSearch unless it is selected.

## 3. Catalog synchronization without Redis

**Today.** `CatalogSynchronization` is the only feature that **requires** Redis. It uses Redis
for:
- a per-tenant lock (`SET NX EX 30m`, renewed per page);
- the checkpoint cursor;
- the current scan id, the seen and known id sets, and per-record manifests;
- a Lua script that swaps the sets at the end of a scan.

Without Redis it returns a typed 503 `DEPENDENCY_UNAVAILABLE` (QB-287,
`CatalogSyncWithoutRedisTest`).

The other Redis users all have fallbacks:
- **Rate limits:** `RedisBackedRateLimiter` falls back to in-memory fixed windows.
- **Analysis cache:** `RedisAIAnalysisCache` ignores failures.
- **Analytics and evidence:** `CommerceAnalytics` and `CommerceEvidenceStore` fall back to
  memory.
- **Platform state:** `PlatformStore` is memory, file or Redis, by configuration.
- **Schemas:** `CommerceSchemaRegistry` reads legacy Redis keys only with the Redis adapter.

**Affected modules:** `qwenbridge-server` (`commerce.CatalogSynchronization`). Optionally a
`SyncStateStore` interface in `qwenbridge-core` (`core.connector`), so that connector packs can
reuse it.

**Behaviour changes:**
- **Store selection:** a new setting `qwenbridge.catalog.state-store` takes `redis`, `file` or
  `memory`. The default is `file` when `qwenbridge.platform.store=file`, otherwise `memory`.
- **Single instance:** with `file` or `memory`, the lock is process-local. Two instances that
  share no store can sync concurrently, so this mode is documented as single-instance.
- **Multiple instances:** these keep `redis`. The 503 then appears only when `redis` is selected
  explicitly and unreachable.

**Migration steps:**
1. Define `SyncStateStore`:
   - `tryLock(key, owner, ttl)`, `renew`, `release`;
   - `cursor(key)`, `saveCursor`;
   - `addSeen`, `seen`, `known`;
   - `swapScan(key)`, which must be atomic.
2. Implement `RedisSyncStateStore` by moving the current code unchanged. Keep the same keys, so
   that existing checkpoints stay valid.
3. Implement `FileSyncStateStore`: atomic file replace under `qwenbridge.platform.file-dir`, and a
   lock file with an owner and expiry. Implement `MemorySyncStateStore` with concurrent maps.
4. Inject the store into `CatalogSynchronization`. Map "store unavailable" to the same typed 503.
5. Run the E2E `LOCAL_PROVIDER_INTEGRATION` mode without Redis. It is currently BLOCKED without
   Redis.

**Acceptance criteria:**
- `CatalogSyncWithoutRedisTest` is replaced by tests that:
  - sync with the `memory` and `file` stores, without Redis;
  - resume from a checkpoint after a restart (`file`);
  - refuse a second concurrent run (409);
  - delete unseen records only for complete-snapshot sources.
- With `redis`, existing checkpoints written by 0.9.1 are resumed (migration test on recorded keys).
- E2E `LOCAL_PROVIDER_INTEGRATION` passes with no Redis running.
- The master-run step results for steps 21 and 24 are unchanged or better.

## 4. Separate Commerce from the server

**Today.** `qwenbridge-commerce-pack` holds the Commerce schema, rules and vocabulary, and depends
only on the plugin SDK. The Commerce **search product** lives in the server:

| Package | Size | Content |
|---|---|---|
| `commerce` | 23 files, 5,580 lines | `CommerceSearchService` (largest), interpretation, extraction, planning, compiler, reranker, recovery, suggestions, sessions, conversation contexts, analytics, evidence, schema registry, catalog sync, HTTP catalog connector, demo datasets, the REST resources |
| `connectors` | 8 files, 912 lines | Backends and filter translation |

The v2 REST contract, `commerce-search/1.0`, carries the Commerce name. The SDKs expose
`CommerceClient`.

**Target separation:**

| Layer | Owns | Must not own |
|---|---|---|
| `qwenbridge-core` | Domain-neutral search engine: QueryIR, validation, planning, retrieval, ranking, recovery, suggestions, conversation | Any field name of a specific domain |
| `qwenbridge-commerce-pack` | Commerce schema, vocabulary, extraction rules, rerank features, relaxation preferences, catalog normalization (variants) | HTTP, CDI, storage |
| `qwenbridge-server` | REST and SSE, identity, tenants, limits, storage adapters, model providers, wiring of packs into the core engine | Commerce-specific logic |

**Affected modules:**
- `qwenbridge-server`: `commerce` and `connectors` shrink to resources and adapters.
- `qwenbridge-core`: gains a domain-neutral `SearchEngine` facade.
- `qwenbridge-commerce-pack`: gains the Commerce rules now in `GroundedCommerceExtraction`,
  `CommerceCompiler`, `CommerceReranker`, `CommerceSuggestions` and `ZeroResultRecovery`.
- SDKs: unchanged.

**Behaviour changes:** none for clients. The REST paths, the `commerce-search/1.0` contract and
the SDK types keep their names; the contract name is an API identifier, not a code location.
Domain packs other than Commerce (Library) get the same search pipeline through
`/api/v2/domains/{domain}`.

**Migration steps:**
1. Write characterization tests for `CommerceSearchService`. Record golden responses for the
   existing Commerce suites and the E2E offline scenarios.
2. Extract the domain-neutral orchestration of `CommerceSearchService.run` into a core
   `SearchEngine`, with interfaces for model analysis, typo correction, expansion and backends.
3. Move the Commerce-specific rules behind `DomainPack` hooks: `understand`, `normalize`,
   `documents`, `policies` and `queryExtension`, plus new rerank and relaxation hooks.
4. Reduce `CommerceSearchService` to an adapter: identity, limits, storage, metrics.
5. Route `DomainExecution` (the Library path) through the same engine.
6. Do proposal 1 first, or at the same time, so that the moved code carries no OpenSearch types.

**Acceptance criteria:**
- The golden comparison is identical for every Commerce suite and E2E scenario.
- `ArchitectureNeutralityTest` gains a rule: no Commerce field names (for example `price`, `brand`,
  `inStock`) in `qwenbridge-core` main sources, outside test fixtures.
- `LibraryPackTest` and `LibraryDomainPipelineTest` exercise planning, validation and recovery
  through the shared engine.
- The server `commerce` package has no class over 500 lines.
- No SDK change. The Java and TypeScript contract tests pass unchanged.

## Order and verification

Proposed order: **3 → 1 (steps 1–3 first) → 4 → 2**.
- Proposal 3 is self-contained and removes the last mandatory use of Redis.
- Proposal 1 removes the last OpenSearch types from paths that proposals 4 and 2 move.

Every step must pass the master verification:
- `scripts/qwenbridge-final-verification.sh`;
- the dual-SDK E2E in all modes;
- the Quarkus-only audit.

Golden-response comparisons record any approved difference in the 1.0 compatibility document.

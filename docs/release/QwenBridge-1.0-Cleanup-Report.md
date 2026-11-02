# QwenBridge 1.0: Repository Cleanup Report (QB-287)

**Date:** 2026-11-02.
**Branch:** `quarkus-branch`, local and uncommitted (HEAD `27b7f39`).
**Decisions:** ADR 0058.

Every number below was measured on this machine, before and after the cleanup, with the same
commands. Where a before value was not measured, the report says so.

## Report A: Structure before and after

| Item | Before | After |
|---|---|---|
| Maven reactor modules (with parent) | 12 | 7 |
| Connector modules | `qwenbridge-connector-elastic` (13 files), `-mongodb` (7), `-postgresql` (9), `-shopify` (9) | none |
| `examples/` | `java-sdk-example` (in the reactor), `typescript-sdk-example`; 13 files, 11 tracked | removed |
| Commerce backends | `memory`, `opensearch`, `elasticsearch`, `postgresql`, `mongodb` | `memory` (default), `opensearch` (optional) |
| Compose files | `docker-compose.yml`, `docker-compose.security-tests.yml`, `docker-compose.table1-tests.yml` | first two |
| Verification fragments | `16-table1-backends.sh`, `17-table1-runtime.sh` | `17-sessions-runtime.sh` |
| Server main source files | 434 (derived: 406 + 25 dead-code + 4 backend/secret classes removed − `OpenSearchFilters` moved in) | 406 (counted) |
| Core main source files | 234 (no core file removed) | 234 (counted) |

**After, the reactor is:**
- `qwenbridge-core`, `qwenbridge-plugin-sdk`;
- `qwenbridge-commerce-pack`, `qwenbridge-library-pack`;
- `qwenbridge-server`, `qwenbridge-java-sdk`.

Outside the reactor are `qwenbridge-typescript-sdk` (npm) and `e2e/dual-sdk` (standalone
consumers).

## Report B: Removed files and reasons

| Removed | Reason |
|---|---|
| The 4 connector modules: sources, POMs, tests, `META-INF/services` | Decision 5: removed completely. None was used by the default runtime; Shopify was test-scope only |
| Server `ElasticsearchBackend`, `PostgreSQLBackend`, `MongoDBBackend`, `ConnectorSecrets` | Adapters for the removed modules |
| Root and server POM entries for the 4 modules (the server's test-scope Shopify dependency included) | Same |
| Tests `BackendLiveIntegrationTest`, `NeutralFilterConformanceLiveTest`, `ConnectorPackPackagingTest`, `ConnectorBoundaryTest`, plus the `builtInDatabaseBackends…` and `ConnectorSecrets` cases | They validated only the removed modules. The neutral filter fixtures were kept as `connectors/NeutralFilterConformance`, used by `OpenSearchConformanceLiveTest` |
| `examples/` (13 files), `docs/examples/*` (2), `JAVA_SDK_EXAMPLE_DIR` | Decision 3: the E2E consumers cover the same calls, and 3 v1 scenarios per SDK were added |
| `docker-compose.table1-tests.yml`, `scripts/verification/16-table1-backends.sh`, the table-1 steps in `verify-release.sh`, the CI Shopify install line, `QWENBRIDGE_CONNECTORS_*` lines, the SpotBugs `PgPool` exclusion | Infrastructure for the removed modules |
| `docs/development/shopify-sandbox.md` | Replaced by `e2e/dual-sdk/provider-adapters/shopify_check.py` |
| Server dead code (25 main files): `decision/DecisionService` and `decision/ai/*` (5); `intent/IntentService` and `intent/ai/*` (5); `rewrite/*` (3); `semantic/*` (6); `StreamingEventType`, `BaseUrl`, `ModelId`, `CacheEntry`, `OpenSearchSearchResponse`, `OpenSearchHit` | No reference anywhere: not injected, not referenced, not a CDI producer or observer. The v1 steps take their values from `AIAnalysisStep` |
| 9 test files (22 test methods) for that dead code | Validated only deleted code |
| Configuration keys `qwenbridge.streaming.{max-concurrent-streams,max-streams-per-client,heartbeat-interval,outbound-buffer-size}` | Read by no code |
| `scripts/verification/__pycache__` | Generated |

**Adapted after the first QB-287 master run failed** (`qb287-master-20261102-154323`, one error in
the live suite):
- **`CatalogSynchronizationIntegrationTest.completeShopifySnapshotsRemoveMissingVariantsAndRefreshPrices`.**
  It synced through the removed `shopify` catalog connector kind ("Unknown catalog connector
  shopify"). It now runs as `completeSnapshotsRemoveMissingVariantsAndRefreshPrices` against a
  test-only complete-snapshot connector (`commerce/testconnector/SnapshotCatalogConnectorFactory`,
  registered through `ServiceLoader` in test resources). The same assertions hold: removal of a
  missing variant, price refresh, currency, text template and embedding.
- **`CommerceBackendFlowIntegrationTest`.** It ran only against the removed Elasticsearch,
  PostgreSQL and MongoDB backends, behind the removed table-1 gate, so it would never have run
  again. It now runs against the default `memory` backend with the live model
  (`QWENBRIDGE_RUN_OLLAMA_IT`), with the same assertions. OpenSearch is not used here, because its
  writes are not refreshed before search; the OpenSearch path keeps its own live tests.
- **Both run and pass live** (3 tests, 0 failures). No other test is gated on a variable that no
  script sets.

**Restored during verification:** `operations/metrics/LatencyHistograms`. The dead-code search
first marked it unused, but it is a CDI `@Produces MeterFilter`. The full test run caught the
error (`CommerceApiTest.latencyHistogramsAndCategorizedErrorsAreExported`), and the class was
restored unchanged.

**Kept on purpose:**
- **OpenSearch:** optional (decision 1).
- **The v1 API and the SDK v1 clients** (decision 2).
- **The Library pack** (decision 4), now with `LibraryPackTest` (5 tests) and
  `LibraryDomainPipelineTest` (2).
- **Catalog sync on Redis** (decision 6).
- **The rate limiters:** confirmed in use.

## Report C: Added and changed

**Code:**
- **`DependencyHealthChecker.active()`:** OpenSearch is probed only when selected.
- **`CatalogSynchronization`:** answers 503 `DEPENDENCY_UNAVAILABLE` without Redis.
- **`OpenSearchFilters`:** package-private; moved from the Elasticsearch module.
- **`NeutralFilterTranslator.openSearch()`:** renamed from `elasticsearch()`.
- **`ExampleConnectorPack`:** test-only.
- **`tsconfig`:** `noUnusedLocals` and `noUnusedParameters`.
- **Compose:** Redis `required: false`.
- **Shebangs:** added to 11 sourced verification fragments.

**Tests:**
- `OpenSearchFiltersTest`;
- `CatalogSyncWithoutRedisTest`;
- 2 `OperationalHealthServiceTest` cases;
- `LibraryPackTest`, `LibraryDomainPipelineTest`;
- `ArchitectureNeutralityTest`: module list, no Shopify code, Redis never gates startup;
- E2E: 3 v1 scenarios per SDK.

**Documentation:**
- **Rewritten:** README; docs index; `getting-started`, `troubleshooting` and `release-process`;
  architecture `overview` (8 diagrams).
- **New:** `class-diagrams` (10), `flows` (15), `sequences` (7), `remediation-proposals` (4
  proposals), ADR 0058.
- **Updated:** `pipeline`, `ai-stack`, `modules`, `docker`, CONTRIBUTING, SECURITY,
  CODE_OF_CONDUCT, the E2E README, the compatibility matrix.
- **Banners and notes:** historical banners on the old records; supersession notes on 9 ADRs.

## Report D: Tests and measurements

| Measure | Before | After | Method |
|---|---|---|---|
| `mvn -o clean install -DskipTests` | 27 s | 24 s | Same command, same machine |
| Distinct artifacts in the reactor | 319 | 309 | `dependency:tree` per module, distinct `group:artifact` |
| Server distinct artifacts | 316 | 307 | Same |
| Server startup, packaged jar, no Redis and no OpenSearch | 1.08–1.26 s (E2E server logs before QB-287) | 0.78–1.06 s | Quarkus "started in"; difference within noise |
| Core tests | 644 | 644 | |
| Commerce-pack tests | 17 | 17 | |
| Library-pack tests | 0 | 5 | New |
| Server tests, plain `mvn clean install` | not measured separately | 970 run, 31 skipped (live tests without services), 0 failed | |
| Server tests, master environment (live Ollama, OpenSearch, Redis) | 1,038, 1 skipped (QB-295 master) | 1,003, 1 skipped, 0 failed | Master suite |
| Java SDK tests | 76 | 76 | |
| TypeScript SDK tests | 70 | 70 | |
| Dual-SDK E2E scenarios | 84 PASS (QB-295 master) | 90 PASS, 0 FAIL, 0 divergent | `--mode ALL`, 119 s |
| Live providers | BLOCKED (no authorization) | BLOCKED (no authorization); Shopify mock API PASS | Not derived from mocks |

**Server tests in the master environment: 1,038 → 1,003 (−35).**
- **Removed:** the connector-only tests and the 22 dead-code test methods, listed in Report B.
- **Added:** `OpenSearchFiltersTest`, `CatalogSyncWithoutRedisTest`, two health tests and
  `LibraryDomainPipelineTest`.
- **Unchanged:** no failing test was skipped or weakened.

**Other checks after the cleanup:**
- **Spotless:** PASS.
- **Code-quality profile:** PASS.
- **`verification-test.sh`:** PASS.
- **Quarkus-only audit:** PASS (0 active, 0 unreviewed).
- **`check-versions.py`:** PASS.
- **`npm audit --omit=dev`:** 0 vulnerabilities.
- **Compose `config -q`:** OK for the default, production and production+opensearch profiles.
- **Secret scan over all 1,418 working-tree text files:** 21 matches, all deliberate test
  fixtures for redaction and threat detection (the AWS documentation example key, fake tokens).
  No real credential was found. `.env` is ignored by git.

**Master runs:**
- **`qb287-master-20261102-154323`: FAIL.** One error: the Shopify-kind catalog-sync test,
  adapted as described in Report B.
- **`qb287-master-c-20261102-155549`: PASS.** Exit 0; 90 passed, 0 failed, 2 warnings (working
  tree not clean; tag `v0.9.1` not on HEAD).
  - Suites: core 644, commerce pack 17, library pack 5, server 1,003 (1 skipped), Java SDK 76,
    TypeScript SDK 70.
  - Quarkus-only audit: PASS.
  - Dual-SDK E2E: 90 PASS, 0 FAIL, 0 divergent.
  - Step changes from QB-295 (92 passed): removed the connector test backends' start and stop
    and the TypeScript example build and install; renamed the table-1 sessions step to "Owned
    conversations, consent, editions, backends and Playground".

## QB-287 status

**PASS.** The database-free offline baseline (master step 21 and the E2E `OFFLINE_BASELINE`) and
the mandatory dual-SDK E2E verification both pass.

## Report E: Breaking changes

1. **Backend identifiers `elasticsearch`, `postgresql` and `mongodb` are gone.** Selecting one
   gives "Unknown backend".
2. **The edition features `elasticsearch`, `postgresql` and `mongodb` are gone.** A licence that
   lists any of them is now refused (`FEATURES_EXCEED_EDITION`), because its features exceed the
   edition matrix. It must be reissued without them.
3. **Catalog synchronization without Redis** now returns `503 DEPENDENCY_UNAVAILABLE` with a
   clear message, instead of an untyped failure.
4. **Readiness (`/api/v1/health/ready`) lists `opensearch` only when it is the selected backend.**
5. **`QWENBRIDGE_CONNECTORS_*` environment variables are ignored.**
6. **`examples/` is removed.** Use the E2E consumers and the README quick starts.

The REST, SSE and SDK contracts are unchanged. The compatibility matrix lists the migrations.

## CI rebuilt for this branch and CodeQL findings fixed (after PR #68)

The first pull-request run (PR #68) failed:
- **Architecture Tests, API Compatibility, Benchmark Smoke.** The jobs built only
  `qwenbridge-server` (`-pl qwenbridge-server`), so its reactor dependencies were never built.
- **TypeScript SDK, Dual-SDK E2E.** `actions/setup-node` was not pinned to a commit SHA, which the
  repository's Actions policy requires.
- **Unit Tests.** `ConversationContextApiTest` and `SuggestionsOnLocalCatalogTest` returned
  `CLARIFICATION_REQUIRED`. The language detector labels their English query as another
  language, so the search asks the model to translate it. The tests passed locally only because a
  real Ollama was running.
- **CodeQL.** 9 new high-severity alerts: 5 × `java/path-injection` in `PlatformStore`, 4 ×
  `java/polynomial-redos`.

**Fixes:**
- **`.github/workflows/ci.yml`, rewritten for the 1.0 reactor:**
  - every job builds the whole reactor (`-am`);
  - `setup-node` is pinned;
  - the four required check names are unchanged;
  - Architecture Tests runs all guard tests plus the Quarkus-only audit;
  - API Compatibility runs the server contract tests and the Java SDK fixture tests;
  - the TypeScript job adds `npm audit --omit=dev`;
  - Docker Release Verification starts the database-free container and waits for readiness.
- **`.github/workflows/master-verification.yml`, new.** It runs
  `scripts/qwenbridge-final-verification.sh`, the same master script as every task, on demand,
  nightly and after every push to `main`. It is not a required pull-request check: it needs the
  model stack and runs for a long time.
- **`check-versions.py`.** It now rejects any workflow action that is not pinned to a full commit
  SHA, and `verification-test.sh` has a self-test for that.
- **The `%test` profile.** It points the model client at an unreachable address, so tests behave
  the same with or without a local Ollama. The two tests now use a deterministic stand-in for
  the model translation.
- **`PlatformStore.file`.** The resolved file must be a direct child of the state directory.
- **Linear, non-recursive regular expressions on user text:**
  - `ProtectedTokens` (`EMAIL`, `HOST`, `JOINED`, `AMOUNT`, `WITH_DIGITS`);
  - `Identifiers.CODE`;
  - `IntentAnalyzer` (`SEQUENTIAL`, `PARALLEL`, `SHARED_TAIL`);
  - `Numerals.TOKEN`, `Sanitizer.CLASS_NAME`, `Lexicon.NUMBER_WITH_UNIT`, `Redactor.URL_USERINFO`.

  A probe of every static pattern in the core and server found more problems than CodeQL
  reported. Several patterns threw `StackOverflowError` on a few thousand characters of
  `1.1.1…` or `x.x.x…`, and others took up to 20 s or 61 s on long runs. All are now linear: a
  few milliseconds on 200,000 characters. `RegexLinearityTest` guards this.
- **Not changed:** `ModelDescriptor.SECRET_KEY`. It only checks configuration metadata keys.

**Local verification of the fixes:**
- full build with all tests and no model reachable: core 648, packs 17 + 5, server 970, Java SDK
  76;
- every CI job command, Spotless, the code-quality profile and the Quarkus-only audit: PASS;
- dual-SDK E2E in all modes: 90/90, 0 divergent.

## Findings not fixed (outside QB-287 scope)

- **The `prod` profile does not fail at startup without API principals.** `ApiIdentity` checks
  them on first use, so the server starts and answers every request with 400 "Authentication
  requires configured principals". The `application.yaml` comment says startup fails. The
  documentation now describes the real behaviour. Making the check eager is a small, separate
  change.
- **`CommerceSearchService` injects an `OpenSearchProvider` it never uses.** This is step 1 of
  remediation proposal 1.
- **The `%test` profile selects `opensearch`**, so most Commerce suites cover the OpenSearch path
  (stubbed), not the default `memory` path. This is step 3 of remediation proposal 1.

## Remaining architectural work

See [remediation proposals](../architecture/remediation-proposals.md):
1. OpenSearch extraction;
2. v1 onto the Core engine;
3. Redis-free catalog sync;
4. Commerce separation.

None is implemented. Each has affected modules, behaviour changes, migration steps and acceptance
criteria.

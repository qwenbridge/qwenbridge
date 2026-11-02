# QwenBridge 1.0: Section 3 Completion Report (Platform, SDKs and Evaluation)

> **Historical record.** It describes the repository at the time it was written. Modules and connectors it mentions may have been removed since: QB-287 removed the Elasticsearch, PostgreSQL, MongoDB and Shopify connector modules and `examples/` (ADR 0058). For the current state, start at [docs/README.md](../README.md).

**Scope:** QB-147 – QB-224, Phases 21–30 (78 tasks)
**Branch / base:** `quarkus-branch` @ `27b7f39`. All changes are local and uncommitted.
**Git commits: 0. Git pushes: 0.**

## 1. Gate result

| Item | Result |
|---|---|
| Final master run | `section3-final-20261101-215857`, **PASS**, exit 0 |
| Workload | Full default: 1,000,000 cached requests at 250 VUs. 1,000,000/1,000,000 completed; HTTP error rate 0; 6,000,000/6,000,000 k6 checks passed; achieved 2,622 req/s over 384 s; p95 119 ms, p99 152 ms. Server: 0 operational errors; heap max 207 MB, container max 677 MB of 1,024 MB; 3.0 Redis commands per request |
| Other load phases | 10,000 requests at 250 VUs: 2,306 req/s, p95 135 ms, 0 errors. Commerce throughput, 10,000 requests at 100 VUs: 1,266 req/s, p95 151 ms, 0 errors, 25.8 Redis commands per request. Cold path (live model): p95 3.15 s |
| Quality phases (live model) | Commerce quality 186/186, language 22/22, retrieval 15/15, security input 18/18, resilience 8/8 (its 4 invalid requests counted as *expected* errors), Table 1 sessions 280/280 |
| Regression gate | 0 failing of 18. Against the Section 2 gate baselines: 1 M cached p95 +0.8 %, Commerce throughput p95 +11.6 %. Core retrieval and quality within tolerance. The injected-slowdown check was detected (+185 %) |
| Checks | 88 passed, 0 failed, 0 skipped, 2 warnings (uncommitted working tree; tag v0.9.1 not on HEAD) |
| Tests | Core 578, server 955 (1 skipped: the opt-in AI benchmark), Commerce pack and connector modules all passing (Shopify sandbox skipped: no credentials), Java SDK 72, TypeScript SDK 66, browser 12 |
| Section 1 and 3 gate steps | Core isolation, no Spring, startup without Redis, OpenSearch and Ollama, and live Java SDK analyze. Live Java and TypeScript v2 clients, TypeScript SDK on Node.js 20, live strategy comparison (4/4 strategies executed) |
| Code quality | SpotBugs 0 findings in every module; Spotless clean |

## 2. Batches and master runs

Intermediate runs used 1,000 requests at 10 VUs; the gate in §1 uses the full default load.

| Batch | Tasks | Master run | Result |
|---|---|---|---|
| 22 | QB-147 – QB-152 Commerce Domain Pack (ADR 0036) | `batch22-master-20261101-140441` | PASS |
| 23 | QB-153 – QB-159 Universal connector SPI (ADR 0037) | `batch23-master-b-20261101-143421` | PASS (second run; see §7) |
| 24 | QB-160 – QB-171 Elasticsearch / OpenSearch connector module (ADR 0038) | `batch24-master-b-20261101-150941` | PASS (second run; see §7) |
| 25 | QB-172 – QB-178 PostgreSQL and MongoDB connector modules (ADR 0039) | `batch25-master-b-20261101-154420` | PASS (second run; see §7) |
| 26 | QB-179 – QB-185 REST and SSE API (ADR 0040) | `batch26-master-20261101-162307` | PASS |
| 27 | QB-186 – QB-192 Java and TypeScript SDKs (ADR 0041) | `batch27-master-b-20261101-170353` | PASS (second run; see §7) |
| 28 | QB-193 – QB-198 Web Component (ADR 0042) | `batch28-master-20261101-173728` | PASS |
| 29 | QB-199 – QB-206 Playground, datasets, E2E, Shopify isolation (ADR 0043) | `batch29-master-b-20261101-182349` | PASS (second run; see §7) |
| 30a | QB-207 – QB-215 Evaluation and measurement (ADR 0044) | `batch30a-master-20261101-185217` | PASS |
| 30b | QB-216 – QB-224 Benchmarks, capacity, regression gates (ADR 0045) | `batch30b-master-20261101-214216` | PASS |

The full benchmark suite ran separately (`bench-full-20261101-202339`, 69 minutes, and
`bench-redis-20261101-213417`). Its machine-readable results are in `evaluation/benchmarks/2026-11-01/`.

## 3. Task status

**69 VERIFIED, 9 PARTIAL, 0 not started (78 tasks).**
- **VERIFIED** means implemented, covered by tests and exercised by a passing master run.
- **Performance tasks** are VERIFIED by real measurements on the documented single-machine
  environment, not by production capacity.

| Phase | Tasks | Status |
|---|---|---|
| 21 Commerce Domain Pack | QB-147 – QB-152 | all VERIFIED except QB-148 |
| 22 Universal connector SPI | QB-153 – QB-159 | all VERIFIED |
| 23 / 24 Elasticsearch and OpenSearch | QB-160 – QB-171 | all VERIFIED except QB-160, QB-166 |
| 25 PostgreSQL and MongoDB | QB-172 – QB-178 | all VERIFIED |
| 26 REST and SSE API | QB-179 – QB-185 | all VERIFIED except QB-182, QB-184 |
| 27 Java and TypeScript SDKs | QB-186 – QB-192 | all VERIFIED |
| 28 Web Component | QB-193 – QB-198 | all VERIFIED except QB-197 |
| 29 Playground and E2E | QB-199 – QB-206 | all VERIFIED except QB-206 |
| 30 Quality, load and resilience | QB-207 – QB-224 | all VERIFIED except QB-207, QB-208 |

### PARTIAL items

| Task | What is done | What is missing, and where |
|---|---|---|
| QB-148 Commerce pack separation | Commerce contracts, schema, policy and extension live behind the pack. `CommerceSeparationTest` keeps an allow-list of 5 server couplings, each with its removal task | The Commerce search code still lives in the server module. Moving it needs the pack vocabulary SPI (QB-053, S4) |
| QB-160 / QB-166 OpenSearch as an optional module | The connector module serves Elasticsearch and OpenSearch through the SPI, with TLS, dialects, dimension validation and typed failures | The OpenSearch **Commerce** path (apiVersion 2) still uses the server's Quarkus REST client and TLS registry; v2 Commerce compilation stays in the server |
| QB-182 Cancellation and deadlines | Deadlines and owner cancellation for every v2 search, turn and stream, through connectors, JDBC, MongoDB and HTTP. They never trip circuit breakers and are tested end to end | The v1 pipeline and AI token streaming are not under the request scope. Model calls are not cancelled inside Ollama: abandoned work keeps running (measured backlog up to 150 s, QB-219) |
| QB-184 Request ID and trace propagation | Propagated to OpenSearch, Ollama and Elasticsearch (`traceparent`, `X-Opaque-Id`), to MongoDB (comment) and to SSE | Not propagated to PostgreSQL statements or the v1 pipeline |
| QB-197 Accessibility | Labelled controls, live regions, `aria-busy`, focus management, Escape cancels; the accessible tree and keyboard-only paths are tested | No automated WCAG audit (axe); Chromium only |
| QB-206 Shopify sandbox E2E | Opt-in `ShopifySandboxTest` and a setup guide; skipped without credentials | Never run against a real store: no sandbox was available (H7) |
| QB-207 Evaluation datasets | Versioned multilingual, multi-domain set (36 documents, 36 queries, graded judgments, tuning and held-out split) and library and Commerce sets with expectations | Judgments come from a single annotator (seed set). An independent labelled benchmark is needed for relevance claims (H7) |
| QB-208 Capability matrix | 11 capabilities mapped to existing tests; `CapabilityMatrixTest` keeps the references valid | One stated gap: no test makes one sub-query's provider fail while the others succeed (multi-intent degradation) |

## 4. Integration scenarios A – J

| Scenario | Evidence | Result |
|---|---|---|
| A. Core without external services | `ServiceFreeE2ETest`: Redis, OpenSearch, PostgreSQL, MongoDB and Elasticsearch unreachable or unconfigured, no Shopify, a controlled local model. Also the Section 1 standalone-startup check | PASS |
| B. Optional Commerce | `CommercePackLifecycleTest` (enabled and disabled), `CommerceSeparationTest` | PASS |
| C. Elasticsearch connector | `ElasticLiveTest`, `NeutralFilterConformanceLiveTest`, `BackendLiveIntegrationTest` | PASS |
| D. OpenSearch connector | `OpenSearchConformanceLiveTest`. Live strategy comparison: keyword, vector, hybrid and planned, 4 of 4 executed | PASS |
| E. Database read-only enforcement | `PostgresLiveTest` (the database refuses with SQLSTATE 25006), `MongoLiveTest`, `BuiltInConnectorsTest` (ACCESS_DENIED → 403) | PASS |
| F. REST and SSE through the SDKs | Live Java and TypeScript v2 client steps; `CommerceClientResilienceTest`; `commerce-resilience.test.ts`; contract fixtures | PASS |
| G. Web Component | 6 browser tests on a plain page, with no framework and no Shopify (`web-component.browser.mjs`) | PASS |
| H. Local E2E on synthetic data | `ServiceFreeE2ETest` on `commerce-demo` 1.0; `LibraryDatasetTest` | PASS |
| I. Embedding provider outage | `CommercePlanningTest.unavailableSemanticsDegradeVisiblyToKeyword`. Live Ollama outage: typed 503 or `REVIEW`, never fabricated semantic results | PASS |
| J. Performance regression | The master step "regression gate detects an injected slowdown": a 5 ms-per-call injection is reported as FAIL (+219.7 %) | PASS |

## 5. Section 3 acceptance criteria

| # | Criterion | Evidence |
|---|---|---|
| 1 | Implementation and verification evidence for QB-147 – QB-224 | §2, §3, ADRs 0036 – 0045 |
| 2 | Commerce is an optional Domain Pack | ADR 0036, Scenario B (code location caveat: QB-148) |
| 3 | Universal connector SPI stable and reused | ADR 0037; Elasticsearch, OpenSearch, PostgreSQL, MongoDB, memory and Shopify catalog sources on it |
| 4 | Elasticsearch and OpenSearch are optional search connectors | ADR 0038 (OpenSearch Commerce path caveat: QB-160, QB-166) |
| 5 | PostgreSQL and MongoDB are optional database connectors | ADR 0039, `DriverDependencyTest` |
| 6 | Redis not required for baseline Core | `ServiceFreeE2ETest` (`qwenbridge.platform.store=memory`). The default Docker configuration still uses Redis for Commerce evidence and analytics |
| 7 | Database drivers are not Core dependencies | `DriverDependencyTest`; the Section 1 Core isolation check |
| 8 | Connector failures are isolated | Circuit breakers (`BuiltInConnectorsTest`); live outages: the memory backend is unaffected by an OpenSearch outage |
| 9 | Credentials and secrets handled securely | Secret references (`env:`, `file:`), no secrets in errors or `toString` (SDK tests); credentials only through `configure()` in the Web Component |
| 10 | REST and SSE contracts versioned and tested | ADR 0040, `ApiEndpointSnapshotTest` (60 endpoints), SSE contract tests |
| 11 | SDKs compatible with supported APIs | ADR 0041; contract fixtures checked against real server responses; live SDK steps; Node.js 20 |
| 12 | Web Component works without Shopify | Scenario G |
| 13 | Developer Playground works locally | ADR 0043, browser tests, live strategy comparison |
| 14 | Core E2E without external services | Scenario A / H |
| 15 | Shopify E2E isolated | `qwenbridge-connector-shopify`, `ShopifyIsolationTest`, opt-in sandbox test |
| 16 | Quality metrics use labelled data | `RetrievalQualityEvaluationTest` on graded judgments (seed caveat: QB-207) |
| 17 | Performance metrics use actual measurements | `docs/evaluation/benchmark-results-2026-11-01.md`; raw JSON |
| 18 | Load tests cover keyword, vector, hybrid and AI | Benchmark suite strategy phases, `AiTransformationBenchmarkTest` |
| 19 | Spike, soak, stress and failure injection executed | Benchmark suite (soak 10 minutes) |
| 20 | One-million-user scenarios without unsupported claims | `docs/evaluation/capacity-scenarios.md` |
| 21 | Benchmark results reproducible | `run-benchmarks.py`, versioned datasets and reports |
| 22 | Regression gates use approved baselines | `evaluation/baselines/baseline.json`, master gate steps |
| 23 | Security and authorization enforced | Invalid credentials get 401 through every outage window; owner-only cancellation; tenant isolation tests |
| 24 | Business logic and contracts preserved or migrated | §6; `docs/api/rest-api.md` migration section |

## 6. Behaviour changes a client or operator can observe

- **Read-only database connectors.** PostgreSQL and MongoDB built-ins are read-only by default; set
  `…_ACCESS=read-write` to write (ADR 0039).
- **v2 API:**
  - **New metadata:** errors gain `category` and `retryable`, and SSE events gain `sequence`,
    `terminal`, `error` and `traceId`.
  - **New endpoints:** `GET /api/v2/version` and `DELETE /api/v2/requests/{id}`.
  - **Deadlines:** a deadline applies to every request (default 60 s; clients may send
    `X-Request-Timeout-Ms`).
  - **Request IDs:** `requestId` must be a safe identifier, and an in-flight duplicate is 409.
  - **Upstream errors** are 502 on v1 and v2.
- **v1:**
  - **Error shape:** the error body keeps exactly 7 fields.
  - **Authenticated deployments:** the v1 refusal now uses the standard error body
    (`TENANT_API_REQUIRED`).
- **SDKs.** The v2 clients throw typed errors, retry only safe operations, send their deadline and
  cancel on abort. Streams send `Accept: text/event-stream`.
- **Web Component:**
  - **States:** it exposes `data-state` and `qwenbridge-state`.
  - **Loading:** the search box is read-only, not disabled, while loading.
  - **Removal:** removing the element cancels its request.
  - **Playground assets:** they now include `errors.js` and the retry modules.
- **Catalog connectors.** The Shopify catalog source is a separate module, found by
  `ServiceLoader`. The server keeps `http-json`, and `CatalogConnector` moved to the Commerce pack.
- **Memory platform store.** With `qwenbridge.platform.store=memory`, Commerce evidence and
  analytics run in process.
- **Demo data.** `qwenbridge.demo.datasets` loads demo data, into the memory backend only.
- **Metrics.** New histograms and counters: `qwenbridge.search.stage`, `qwenbridge.api.errors`,
  `qwenbridge.api.inflight`.

## 7. Defects found and fixed during Section 3

- **Lost SSE contract (B26).** The v2 search stream dropped the request's `contract` field.
- **Duplicate headers (B26).** `X-Request-ID`, `traceparent` and `X-Trace-Id` were sent twice.
- **Leaked upstream detail (B26).** Upstream REST errors leaked their status and internal client
  method names.
- **SDK stream rejection (B27).** Both SDKs sent `Accept: application/json` on streams, which the
  server refuses with 406. Found by the new live SDK checks.
- **Silently dropped fields (B27).** The Java SDK dropped `queryIR.fields`, hit `metadata` and
  `results.empty`. Found by the model-coverage test.
- **Master runs that failed on test or tooling gaps (no product defect):**
  - **B23:** a new API test assumed Elasticsearch and PostgreSQL were unconfigured.
  - **B24:** the verification fixture lacked the new module pom.
  - **B25:** a live test seeded data through the newly read-only built-ins, and now opts into
    writes.
  - **B27:** the Playground's copy of the TypeScript client was stale and incomplete.
  - **B29:** the demo seeder was placed outside the Commerce layer.

## 8. Findings from measurement (documented, not fixed in 1.0)

From `docs/evaluation/benchmark-results-2026-11-01.md` and `quality-evaluation.md`:
1. **The single model slot is the bottleneck** for vector and hybrid search (about 55 req/s, query
   embedding), uncached analysis (about 0.3 req/s) and multilingual Commerce queries (translation
   by the model on every request, uncached). A mixed workload at 20 req/s collapses for this reason.
2. **Abandoned model work keeps running** inside Ollama and blocks later requests for up to 150 s.
3. **Keyword retrieval saturates between 1,200 and 1,600 req/s** on one 1 GiB container. Memory
   reached 965 MB of the limit and was not released afterwards.
4. **Commerce search is unavailable without the model,** even on the in-process backend
   (fail-closed safety). During a model outage, hybrid answers 503 while vector answers `REVIEW`.
5. **Hybrid was not the best strategy** on the evaluation set; vector-only ranked best. Vector
   retrieval has no similarity threshold, so it always returns K results.
6. **Lingua misclassifies some short English queries** (one as Danish), which triggers a model
   translation.
7. **apiVersion 2 and `commerce-search/1.0` treat exclusions on missing fields differently;** the
   demo dataset records both expectations.

## 9. Known gaps carried into Section 4

- **PARTIAL items** in §3.
- **Redis** is still the default store for Commerce evidence, analytics and the analysis cache in
  the Docker configuration.
- **The v1 pipeline** still uses its own ranker and `InMemorySearchProvider`.
- **Elasticsearch, PostgreSQL and MongoDB** are keyword-only on the SPI.
- **Pre-existing:** the generic 400 mapper quirk.
- **Open security findings:** S1 (CORS), S8 – S11, S13.
- **Performance evidence** comes from one laptop. A production-like environment needs its own runs
  and approved baselines.

## 10. Evidence

- **Logs:** `logs/qwenbridge-1.0/*.console.log`, each with a `*.meta` file (run id and exit code),
  for every batch and for this gate. Performance phases: `logs/verification/performance-<run>/`.
- **Benchmarks:** `evaluation/benchmarks/2026-11-01/`, `evaluation/baselines/baseline.json`.
- **Datasets:** `evaluation/datasets/`, `evaluation/capability-matrix.json`, `qwenbridge-core/src/test/resources/datasets/`, `qwenbridge-server/src/main/resources/datasets/`.
- **ADRs:** 0036 – 0045 in `docs/adr/`.
- **Documentation:**
  - API: `docs/api/rest-api.md`, `docs/api/sse.md`;
  - SDKs: `docs/sdk/`;
  - development: `docs/development/playground.md`, `docs/development/shopify-sandbox.md`;
  - evaluation: `docs/evaluation/`.

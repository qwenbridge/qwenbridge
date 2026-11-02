# QwenBridge 1.0: Section 2 Completion Report (Adaptive Retrieval and Filterless Commerce)

> **Historical record.** It describes the repository at the time it was written. Modules and connectors it mentions may have been removed since: QB-287 removed the Elasticsearch, PostgreSQL, MongoDB and Shopify connector modules and `examples/` (ADR 0058). For the current state, start at [docs/README.md](../README.md).

**Scope:** QB-085 – QB-146, Phases 13–20
**Branch / base:** `quarkus-branch` @ `27b7f39`. All changes are local and
uncommitted.
**Git commits: 0. Git pushes: 0.**

## 1. Gate result

| Item | Result |
|---|---|
| Final master run | `section2-final-20261101-132712`, **PASS**, exit 0 |
| Workload | Full default: 1,000,000 cached requests at 250 VUs. 1,000,000/1,000,000 completed, HTTP error rate 0, 6,000,000/6,000,000 k6 checks passed, p95 118 ms, p99 151 ms. |
| Other load phases | 10,000 at 250 VUs: p95 141 ms, 0 errors. Commerce throughput (10,000 requests at 100 VUs): p95 135 ms, 0 errors. Cold path (live model): p95 3.16 s. |
| Quality phases (live model) | Commerce quality 186/186, language 22/22, retrieval 15/15, security input 18/18, resilience 8/8, Table 1 sessions 280/280 |
| Checks | 82 passed, 0 failed, 0 skipped, 2 warnings (uncommitted working tree; tag v0.9.1 not on HEAD) |
| Tests | Core 554, Commerce pack 14, server 918, Java SDK 62. All Section 1 gate checks pass: Core isolation, no Spring, startup without Redis, OpenSearch and Ollama, live Java SDK. |
| Live tests in the gate | Multilingual Commerce 41/41; OpenSearch kNN on BGE-M3 1/1; hybrid retrieval with BGE-M3 1/1; neutral-filter conformance on Elasticsearch, PostgreSQL and MongoDB 3/3; OpenSearch conformance 2/2; Ollama pipeline 3/3; Redis platform store 2/2 |
| Code quality | SpotBugs 0 findings in every module; Spotless clean |

## 2. Batches and master runs

| Batch | Tasks | Master run | Result |
|---|---|---|---|
| 13 | QB-107 – QB-111 Retrieval primitives (ADR 0025) | `batch13-master-20261101-013610` | PASS |
| 14 | QB-112 – QB-116 Hybrid retrieval and fusion (ADR 0026) | `batch14-master-20261101-014958` | PASS |
| 15a | QB-099 – QB-106 Planning in Core (ADR 0027) | `batch15a-master-20261101-020829` | PASS |
| 15b | QB-100 and QB-107 Connectors on the unified SPI (ADR 0028) | `batch15b-master-20261101-022410` | PASS |
| 16 | QB-092 – QB-098 Catalog intelligence (ADR 0029) | `batch16-master-20261101-094713` | PASS |
| 17a | QB-091, QB-085 (registry) and QB-107 OpenSearch (ADR 0030) | `batch17a-master-c-20261101-102732` | PASS (third run; see §6) |
| 17b | QB-085 – QB-090 Commerce Search Contract 1.0 (ADR 0031) | `batch17b-master-20261101-112133` | PASS |
| 18 | QB-117 – QB-124 Ranking (ADR 0032) | `batch18-master-20261101-115344` | PASS |
| 19 | QB-125 – QB-131 Zero-result recovery (ADR 0033) | `batch19-master-20261101-121659` | PASS |
| 20 | QB-132 – QB-138 Suggestions, refinement, clarification, feedback (ADR 0034) | `batch20-master-b-20261101-124536` | PASS (second run; see §6) |
| 21 | QB-139 – QB-146 Conversation context (ADR 0035) | `batch21-master-20261101-131214` | PASS |

The intermediate runs used 1,000 requests at 10 VUs. The gate in §1 uses the
full default load.

## 3. Task status

58 VERIFIED, 4 PARTIAL, 0 not started (62 tasks). "VERIFIED" means implemented, covered by tests and exercised by a
passing master run. Every PARTIAL item states what is missing and where it is
planned.

| Phase | Tasks | Status |
|---|---|---|
| 13 Filterless Commerce | QB-085 – QB-091 | all VERIFIED except QB-086 |
| 14 Product catalog intelligence | QB-092 – QB-098 | all VERIFIED |
| 15 Query compilation and adaptive planning | QB-099 – QB-106 | all VERIFIED |
| 16 Retrieval engine | QB-107 – QB-116 | all VERIFIED |
| 17 Ranking | QB-117 – QB-124 | all VERIFIED except QB-124 |
| 18 Relaxation and zero-result recovery | QB-125 – QB-131 | all VERIFIED except QB-129 |
| 19 Suggestions and refinement | QB-132 – QB-138 | all VERIFIED except QB-136 |
| 20 Stateless session and context | QB-139 – QB-146 | all VERIFIED |

### PARTIAL items

| Task | What is done | What is missing, and where |
|---|---|---|
| QB-086 Commerce interpretation | The universal QueryIR is mapped onto the Commerce schema by the Core validator. Values are canonicalized with the pack vocabulary. No Commerce field is in QueryIR. | The interpretation and its mapping rules live in the server (`CommerceInterpretation`, `DomainMappings`) until the pack vocabulary SPI ships them with the pack (QB-053, S4). |
| QB-124 Ranking quality | nDCG@k, MRR and precision@k, checked against hand-computed values. Determinism tests. A seed set of graded judgments (q1: nDCG@3 0.934 against 0.431). | Quality claims need a representative, independently labelled judgment set (audit H7). |
| QB-129 Validated alternatives | `AlternativeValidator` checks planner proposals and stored offers, and re-validates on accept (a tampered offer gets 409). | Model rewrite suggestions are still plain text. Validating them needs their constraints extracted first. |
| QB-136 Negative feedback | Three typed feedback kinds become explicit updates applied to the follow-up search. | Feedback updates are not yet written into a session or stateless context. The context contract already has `suppressed` for this. |

## 4. Architecture added in Section 2 (Core, no dependencies beyond Jackson)

- **`core.retrieval`:**
  - the unified `SearchProvider` SPI: tenant-mandatory requests,
    capabilities, typed outcomes and cancellation;
  - the neutral `Filter` AST with reference semantics;
  - in-memory BM25 and cosine vector indexes;
  - `HybridRetriever` with RRF fusion and labelled degradations;
  - `ManagedVectorIndex`.
- **`core.embedding`:** the embedding SPI and stage, embedding spaces and
  reindex detection.
- **`core.planning`:** `FilterCompiler`, `RetrievalPlan` 1.0,
  `AdaptivePlanner`, policies and `PlanExecutor`.
- **`core.ranking`:** a versioned policy, the deterministic baseline, the
  single reranker SPI with its guard stage, business rules, re-validation and
  metrics.
- **`core.recovery`:** result assessment, a typed relaxation policy and
  planner, an alternative validator and reason codes.
- **`core.suggestion`:** typed grounded suggestions, refinement, feedback and
  clarifications.
- **`core.conversation`:** the context contract, limits, validator, merge and
  Ed25519 tokens.

In the Commerce pack:

- the canonical product with provenance;
- the catalog mapping SPI (JSON feed and Shopify);
- the vocabulary;
- the versioned product text.

In the server:

- all five connectors (OpenSearch, Elasticsearch, PostgreSQL, MongoDB and the
  new in-memory backend) on the unified SPI, with OpenSearch and in-memory
  vector sides;
- the opt-in `commerce-search/1.0` contract;
- the platform store adapters.

## 5. Behaviour changes a client can observe

All changes are additive unless listed here. apiVersion 2 responses without
`contract` are unchanged; a test asserts that no new keys appear.

- **New endpoints:**
  - `POST /api/v2/catalog/canonical`;
  - `GET /api/v2/search/{id}/recovery`;
  - `POST /api/v2/search/{id}/suggestions/{sid}/apply`;
  - `POST /api/v2/search/{id}/feedback`;
  - `GET /api/v2/sessions/{id}/context`;
  - `POST /api/v2/conversations/turns`.
- **New request field:** `contract: "commerce-search/1.0"`. Its responses add
  `retrieval`, `interpretation`, `subqueries` and `clarifications`, and the new
  statuses `UNSUPPORTED`, `PARTIAL` and `UNAVAILABLE`.
- **Unsupported search mode:** returns 422 as documented (previously 500).
- **Backends:** `GET /api/v2/backends` lists `memory`. A request may name any
  configured backend; the hard-coded list is gone, and an unknown name is still
  400.
- **Catalog:**
  - Known brands use their canonical spelling (`NIKE` → `Nike`), and
    attribute-name aliases merge.
  - Shopify variants keep all their options.
  - Indexed documents gain `productTextTemplate` and
    `productTextFingerprint`.
  - Sync embeds once per product, from the versioned text.
- **v2 rerank:** keeps the backend score in `metadata.retrievalScore`.
- **Suggestions:** `GET …/suggestions` adds `version` and `items`; relaxation
  proposals add `kind` and `requiresConsent`.
- **v1:** hits name their ranking policy (`rankingPolicy`).

## 6. Defects found and fixed during Section 2

- **Elasticsearch ids (B15b).** Elasticsearch results returned internal `_id`
  digests instead of document ids.
- **422 mapped to 500 (B17a).** An unsupported search mode returned 500. The
  error mapper turned every status missing from the JAX-RS enum (such as 422)
  into 500.
- **1.0 paging (B17b).** It could never reach a second page: the planner
  fetched only the current page, so it never knew more existed. Caught by a
  test before the master run.
- **Master runs that failed on test defects (none were product defects):**
  - **B17a, first run:** the new OpenSearch conformance test read the TLS test
    instance's URL instead of the application's.
  - **B17a, second run:** a Table 1 runtime check pinned the exact four-backend
    list.
  - **B20, first run:** a pre-existing rate-limiter test had a wall-clock window
    race. It now uses a fixed clock, and a test was added for the window reset.

## 7. Known gaps carried into Section 3

- **Redis remains in use.**
  - **Which components.** Analytics, evidence, the schema registry and the
    analysis cache use Redis directly.
  - **Readiness.** The Quarkus Redis readiness check remains.
  - **Plan.** QB-149 makes Redis an optional connector.
  - **Already done.** The platform store, and so sessions and offers, is now a
    selectable adapter.
- **Retrieval limits:**
  - Elasticsearch, PostgreSQL and MongoDB are keyword-only on the unified SPI.
  - Paging under the 1.0 contract reaches 30 results.
  - Temporal constraints are refused (`UNSUPPORTED`) because the filter
    compiler cannot compile dates.
- **The v1 analysis pipeline** still uses its own weighted ranker and
  `InMemorySearchProvider`. Moving v1 onto the planner and Core ranking is
  separate work.
- **Mapper quirk.** The generic 400 mapper replaces messages with "Malformed
  JSON request body". It is pre-existing and was left unchanged; new endpoints
  report their reasons through the argument mapper.
- **Open security findings** from Section 1: S1 (CORS), S8, S9, S10, S11, S13.
  S14 (connector read-only mode) is unchanged.

## 8. Evidence

- **Logs:** `logs/qwenbridge-1.0/*.console.log`, each with a `*.meta` file
  (run id and exit code), for every batch and for this gate.
- **ADRs:** 0025 – 0035 in `docs/adr/`.
- **API documentation:** `docs/platform/table-1-api.md` (catalog, backends,
  Contract 1.0, recovery, suggestions and context sections).

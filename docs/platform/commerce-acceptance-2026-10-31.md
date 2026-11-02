# Acceptance record for the 19 selected phases — 2026-10-31

Implementation targets Java 25 and Quarkus/CDI. The historical 50-phase audit is
the starting baseline. This change addresses the selected nineteen phases; it
does not implement the other twenty-five absent roadmap phases or restore Spring.

| Phase | Implementation | Verification |
|---|---|---|
| 3 | English/catalog-language translation, source corroboration, mixed scripts/digits, preserved typed constraints | CommerceMultilingualIntegrationTest: 11 languages + mixed, Swedish target; CommerceExtractionTest |
| 4 | Typed intents, explicit uncertainty/questions, safe unclear input clarifies before execution | Live PRODUCT_SEARCH and 4-language COMPARE cases; ambiguity and legacy pipeline regression |
| 5 | Typed attributes/operators with original UTF-16 spans and independent known-attribute rules | Adversarial category/brand/color/size/price/currency/stock/condition products; extraction tests |
| 7 | Immutable tenant schema versions, type/operator/normalization/backend mapping, Redis registration | Compiler tests and registry recreation/isolation live test |
| 8 | Decimal/currency/date/boolean validation, range/equality contradictions, shared public execution compiler | Compiler/API tests, conflicting sizes, actual backend product postcondition checks |
| 10 | Model ALLOW/REVIEW/BLOCK combined with deterministic rules; unavailable model stops execution | ModelSafetyStepTest, CommerceApiTest outage/review, actual malicious model query |
| 11 | Verified Bearer subject/tenant/roles, resource ownership, V1 tenant bypass guard | 401/403, forged tenant, cross-tenant and same-tenant/different-subject tests |
| 12 | Source-linked decision explanation, separate uncalibrated confidence signals, calibration evaluation | Evidence retrieval assertions, Brier/ECE reference calculation, calibrated=false API contract |
| 13 | Sentence → typed plan → validated real backend filters → matching final products | Live API modes and seven-condition multilingual corpus; k6 product assertions |
| 15 | OpenSearch keyword/vector/hybrid plans, filtered Lucene kNN, strict missing/unsupported behavior | Live three-mode execution, price/currency/operator boundaries, demo backend rejection |
| 17 | Model or external plugin reranker, constrained ID permutation, bounded workers/timeouts/fallback | Real model controlled nDCG uplift, external JAR contract, invalid IDs and timeout tests |
| 19 | Owned suggestion API and refinement, field replacement and explicit relaxation, unaffected constraints retained | Live size replacement, validated feedback/ownership and explicit any-size relaxation |
| 22 | HTTP JSON and Shopify adapters, product/variant pagination, actual source currency, retry/checkpoint/lease, embeddings and snapshot deletion | Independent HTTP protocol tests, failed-page replay, tenant isolation and repeated Shopify snapshot test |
| 24 | Service Basic/Bearer auth, trusted TLS CA, real secured OpenSearch | Isolated TLS backend: accepted CA/credentials, rejected certificate/password, tenant/boundary filters; client error tests |
| 26 | Versioned V2 REST/SSE and typed Java/TypeScript commerce clients | API tests, real HTTP SDK contract tests, split UTF-8 SSE and terminal/error cases |
| 30 | Bounded tenant search/click events, reference retrieval/calibration evaluation and quality gates | Precision@K, Recall@K, MRR, nDCG/Brier/ECE reference values; live reference product assertions |
| 31 | Separate public JDK-only plugin SDK, versioned manifest/capabilities, ServiceLoader JAR loading | Independent plugin compilation and JAR execution using only the SDK |
| 34 | Ollama + second HTTP JSON provider, capability manifest and provider discovery | Chat, structured schema, streaming and embedding through actual local HTTP; Ollama live suite |
| 44 | Source IDs/URNs, source/observation timestamps, retrievable original text, exact spans, parent chains | Live Redis evidence persistence, tampered-span rejection and tenant/ownership checks |

Read [the API and deployment contract](commerce-api-v2.md) for configuration,
retention and capability boundaries. The language corpus is controlled and small;
it does not certify every language or arbitrary merchant schemas. Whole-sentence
model evidence is broad grounding, not a precise semantic proof of each field.
Confidence is not advertised as a probability. The reranking improvement is a
controlled reference result, not a production relevance guarantee.

The catalog adapters were validated with independent local HTTP endpoints. A real
Shopify merchant and external AI gateway are not provisioned or certified by these
tests. Amazon, Elasticsearch, PostgreSQL and MongoDB connectors remain separate
roadmap work. The plugin loader runs trusted administrator code and has no sandbox
or marketplace lifecycle. V2 refinement is bounded by retained search TTL/capacity.

## Final complete execution

Run **20261031-094646** completed with exit code 0 using the actual
`bash scripts/qwenbridge-final-verification.sh` entry point: **75 checks passed,
0 failed, 0 skipped**. Its two Git warnings identify the pre-existing dirty tree
and optional v0.9.1 tag absent from HEAD; this is local acceptance, not a tagged
release publication. All 681 Java tests (642 server + 39 SDK) and 36 TypeScript
tests passed with no skips. Spotless/SpotBugs, packaging, seed, live Redis/Ollama,
secured TLS OpenSearch, real HTTP timeout and public V2 REST/refinement/evidence/SSE
checks passed.

| Workload | Requests / VUs | p95 | p99 | Failed checks / HTTP errors |
|---|---:|---:|---:|---:|
| Cold analysis, models resident | 10 / 1 | 3,143.71 ms | 3,151.63 ms | 0 / 0 |
| Main warm corpus | 1,000,000 / 250 | 50.89 ms | 59.83 ms | 0 / 0 |
| Commerce multilingual quality | 12 / 1 | 4,251.15 ms | 4,279.44 ms | 0 / 0 |
| Commerce repeated query after warm-up | 10,000 / 100 | 65.82 ms | 116.25 ms | 0 / 0 |

The main load completed 6,000,000 assertions at 5,322.19 requests/second in
187.89 seconds. Every main-load request reported an analysis cache hit. The cold
sample reported ten misses. All other staged/security/language/retrieval/resilience
workloads passed; every stage had zero Redis pool rejections or cache fallbacks.
The multilingual commerce workload checks all seven requested attributes and
allows only the reference product, excluding each deliberately incorrect seed
variant. The live JUnit suite also checks category, all retrieval modes,
comparisons, contradictory constraints, catalog-language output, explicit
relaxation and exact evidence after casing/whitespace changes.

Environment: Docker Desktop 8 CPUs / 24,899,309,568 bytes; application Temurin
25.0.5+8, Quarkus 3.40.1, Qwen2.5 digest 845dbda0ea48 and BGE-M3 digest
790764642607. All four services remained healthy with no OOM/restarts. Normal
60/IP and 120 AI quotas were restored; the isolated security backend was stopped.
Agent debugger probes were removed, preserving the user's original breakpoints.

Raw evidence (ignored generated files):

- `logs/verification/verify-release-20261031-094646.log`
- `logs/verification/verify-local-20261031-094646.log`
- `logs/verification/performance-20261031-094646/results.json` and each workload log/summary.
- `qwenbridge-server/target/surefire-reports/` and `qwenbridge-java-sdk/target/surefire-reports/`.

The [portable JSON record](commerce-acceptance-2026-10-31.json)
retains the curated results even after generated logs/target files are cleaned.
No commit, tag or external publication was performed.


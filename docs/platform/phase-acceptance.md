# 50-phase implementation audit — 2026-10-30

This is the historical starting baseline. The subsequent selected 19-phase work
and its current execution evidence are recorded in
[commerce acceptance](commerce-acceptance-2026-10-31.md). Do not use the counts below
as the status after that implementation.

This audit examines the **current local code**, including the uncommitted recovered
`intelligence` package. All Java modules, the Docker build/runtime and CI use **Java 25**.
The server is **Quarkus/CDI**. There is no requirement to restore Spring Boot.

**The 50 product phases are not complete.** A green Maven build validates existing
tests. It does not execute absent features. Live external integration tests
are opt-in in ordinary Maven runs; the complete verification entry point prepares
dependencies and enables them. The previous README claimed absent platform
features and linked missing documents; those claims have been corrected.

Status definitions:

- **implemented**: the scoped capability has real code and tests; limitations and live requirements are stated per row. This is not a production-readiness certification.
- **partial**: a foundation exists but at least one requested product acceptance requirement is missing.
- **missing**: no implementation of the requested capability was found. Similar class names are not accepted as evidence.

Current count: **6 implemented, 19 partial, 25 missing**.

The [consolidated remaining-work backlog](remaining-phases.md) defines the work,
acceptance criteria and proposed priorities for all 44 unfinished phases.
Subsequent compact-output quality tests add small live checks for Persian
translation, English negation/budget preservation and comparison intent; phase 3
remains partial until the broader translation and mixed-language contract is met.

## Per-phase evidence

| Phase | Capability | Status | Source / test evidence | Findings and remaining acceptance work |
| --- | --- | --- | --- | --- |
| 1 | Multilingual Input Foundation | **implemented** | [source](../../qwenbridge-server/src/main/java/io/qwenbridge/input/model/MultilingualInput.java) / [tests](../../qwenbridge-server/src/test/java/io/qwenbridge/input/model/MultilingualInputTest.java) | Immutable original input, language/locale/source metadata and backwards-compatible API request constructors. Tests cover supported-language samples; this does not certify model quality. |
| 2 | Language Detection and Normalization | **implemented** | [source](../../qwenbridge-server/src/main/java/io/qwenbridge/normalization/service/DefaultInputNormalizer.java) / [tests](../../qwenbridge-server/src/test/java/io/qwenbridge/normalization/service/DefaultInputNormalizerTest.java) | Ordered Unicode, URL, HTML, whitespace and control-character rules with trace. LanguageServiceTest covers 11 realistic language samples. Short Latin input intentionally falls back to English; declared language is currently trusted as a hint. |
| 3 | Translation and Mixed-Language Understanding | **partial** | [source](../../qwenbridge-server/src/main/java/io/qwenbridge/rewrite/RewriteService.java) / [tests](../../qwenbridge-server/src/test/java/io/qwenbridge/analysis/ai/CompactAnalysisQualityIntegrationTest.java) | AI rewrites, original-query fallback and three small live quality checks exist. A versioned translation contract, broad mixed-language corpus preserving all supported constraints, and semantic-equivalence acceptance gate remain unfinished. |
| 4 | Intent Detection | **partial** | [source](../../qwenbridge-server/src/main/java/io/qwenbridge/intent/IntentService.java) / [tests](../../qwenbridge-server/src/test/java/io/qwenbridge/intent/IntentServiceTest.java) | Intent enum, confidence, parser and UNKNOWN fallback exist. An interactive ambiguity/disambiguation protocol and measured multilingual intent quality are absent. |
| 5 | Entity and Constraint Extraction | **partial** | [source](../../qwenbridge-server/src/main/java/io/qwenbridge/intelligence/extraction/QueryEntityConstraintExtractor.java) / [tests](../../qwenbridge-server/src/test/java/io/qwenbridge/intelligence/extraction/QueryEntityConstraintExtractorTest.java) | Grounded entity spans and English under/below/up-to prices in USD/EUR/SEK. No general date, time, place, range, multilingual amount or negation extraction. The public AI analysis maps entities to UNKNOWN. |
| 6 | Typed Intermediate Representation | **implemented** | [source](../../qwenbridge-server/src/main/java/io/qwenbridge/intelligence/ir/QueryIR.java) / [tests](../../qwenbridge-server/src/test/java/io/qwenbridge/intelligence/ir/QueryIRMapperTest.java) | Immutable version-1 IR validates entity evidence spans and rejects other versions. Covers the existing entity/price vocabulary; not a full commerce schema and not attached to the public pipeline. |
| 7 | Schema Registry | **partial** | [source](../../qwenbridge-server/src/main/java/io/qwenbridge/intelligence/schema/QuerySchemaRegistry.java) / [tests](../../qwenbridge-server/src/test/java/io/qwenbridge/intelligence/schema/QuerySchemaValidatorTest.java) | Static allowlist for price, BRAND and PRODUCT. No schema registration/update API, schema version lifecycle or backend mapping registry. |
| 8 | Constraint Validation and Mapping | **partial** | [source](../../qwenbridge-server/src/main/java/io/qwenbridge/intelligence/constraint/QueryConstraintValidator.java) / [tests](../../qwenbridge-server/src/test/java/io/qwenbridge/intelligence/constraint/QueryConstraintValidatorTest.java) | Logical allowlists, duplicates, evidence and resource budgets exist in the opt-in structured path. The public API does not run this path. Normal OpenSearch compilation now rejects nonempty uncompiled constraints rather than dropping them. |
| 9 | Security Input Validation | **implemented** | [source](../../qwenbridge-server/src/main/java/io/qwenbridge/threat/ThreatService.java) / [tests](../../qwenbridge-server/src/test/java/io/qwenbridge/threat/ThreatServiceHybridCoverageTest.java) | Deterministic injection/payload detectors, YAML rules, correlation and pipeline stop-before-AI behavior have unit/API coverage. Rule coverage is not proof against every novel payload. |
| 10 | AI Threat Detection | **partial** | [source](../../qwenbridge-server/src/main/java/io/qwenbridge/threat/ThreatService.java) / [tests](../../qwenbridge-server/src/test/java/io/qwenbridge/threat/ThreatServiceCorrelationIntegrationTest.java) | Hybrid in test names means combined rule/correlation detectors. No AI threat-classifier invocation or model/rule arbitration contract is implemented. |
| 11 | Policy and Authorization Engine | **partial** | [source](../../qwenbridge-server/src/main/java/io/qwenbridge/pipeline/step/PolicyStep.java) / [tests](../../qwenbridge-server/src/test/java/io/qwenbridge/pipeline/step/PolicyStepTest.java) | Policy checks rewrite content and breadth before execution. X-API-Key is used for rate limiting, not authentication. No principal/tenant identity, roles, resource permissions or identity-based authorization. |
| 12 | Explainability and Confidence Model | **partial** | [source](../../qwenbridge-server/src/main/java/io/qwenbridge/confidence/ConfidenceService.java) / [tests](../../qwenbridge-server/src/test/java/io/qwenbridge/confidence/ConfidenceAssessmentTest.java) | Conservative minimum of intent/semantic/decision signals; absent analysis scores zero. API and SDKs expose uncalibrated confidenceAssessment. Rule-based blocks identify their method. No calibrated probability model or general evidence graph. |
| 13 | Filterless Commerce Core | **partial** | [source](../../qwenbridge-server/src/main/java/io/qwenbridge/pipeline/step/DecisionStep.java) / [tests](../../qwenbridge-server/src/test/java/io/qwenbridge/pipeline/step/DecisionStepTest.java) | Natural-language retrieval works, but public DecisionStep executes ordinary SearchRequest without QueryIR/constraint compilation. Price language in a query is not an enforced filter. |
| 14 | Product Catalog Intelligence | **missing** | No matching implementation or acceptance tests | Only the demo seed index and generic document maps exist. No product ingestion/normalization service, catalog schema, product relations or catalog-quality tests. |
| 15 | Query-to-Filter Compilation | **partial** | [source](../../qwenbridge-server/src/main/java/io/qwenbridge/execution/provider/opensearch/query/OpenSearchQueryFactory.java) / [tests](../../qwenbridge-server/src/test/java/io/qwenbridge/execution/provider/opensearch/query/OpenSearchStructuredQueryFactoryTest.java) | Opt-in OpenSearch keyword price/currency filters exist. Structured vector/hybrid and other backends are rejected or absent; no cross-backend typed filter acceptance suite. |
| 16 | Keyword, Vector and Hybrid Search | **implemented** | [source](../../qwenbridge-server/src/main/java/io/qwenbridge/execution/provider/implementation/OpenSearchProvider.java) / [tests](../../qwenbridge-server/src/test/java/io/qwenbridge/execution/provider/opensearch/integration/OpenSearchHybridRetrievalIntegrationTest.java) | OpenSearch keyword, kNN and bool-should hybrid retrieval plus embedding provider exist. The live test is opt-in and needs BGE-M3 and seeded products. Hybrid is combined scoring, not an independently tuned fusion model. |
| 17 | Ranking and Reranking | **partial** | [source](../../qwenbridge-server/src/main/java/io/qwenbridge/ranking/service/SearchResultRanker.java) / [tests](../../qwenbridge-server/src/test/java/io/qwenbridge/ranking/service/SearchResultRankerTest.java) | Ranking no longer clips all raw scores above one into a tie. QueryTermReranker provides a replaceable deterministic baseline when requested; disabled mode retains NoOpReranker. No learned/cross-encoder model or validated uplift benchmark. |
| 18 | Query Relaxation and Zero-Result Recovery | **missing** | No matching implementation or acceptance tests | No zero-hit trigger, relaxation planner, preserved hard-constraint policy or recovery acceptance tests. |
| 19 | Search Suggestions and Refinement | **partial** | [source](../../qwenbridge-server/src/main/java/io/qwenbridge/rewrite/RewriteService.java) / [tests](../../qwenbridge-server/src/test/java/io/qwenbridge/rewrite/RewriteServiceTest.java) | AI rewrites exist. There is no suggestion/refinement API, candidate ranking or iterative user refinement state. |
| 20 | Search Session and Conversation Context | **missing** | No matching implementation or acceptance tests | StreamingSessionRegistry tracks SSE subscriptions, not conversation memory. No persisted turns, carried constraints, session isolation or context-expiry contract. |
| 21 | Commerce Domain Pack | **missing** | No matching implementation or acceptance tests | No installable commerce pack, pack manifest or commerce domain model/contract. |
| 22 | Commerce Provider Connectors | **partial** | [source](../../qwenbridge-server/src/main/java/io/qwenbridge/execution/provider/spi/SearchProvider.java) / [tests](../../qwenbridge-server/src/test/java/io/qwenbridge/execution/provider/registry/DefaultSearchProviderRegistryTest.java) | Generic provider SPI/registry/resolver exist. They are not a catalog synchronization/ingestion connector SPI. Shopify is not present in source, tests or inspected Git history. |
| 23 | Elasticsearch Connector | **missing** | No matching implementation or acceptance tests | No Elasticsearch client, credential configuration, connector, mappings or compatibility tests. OpenSearch implementation is not evidence of Elasticsearch support. |
| 24 | OpenSearch Connector | **partial** | [source](../../qwenbridge-server/src/main/java/io/qwenbridge/execution/provider/opensearch/client/OpenSearchClient.java) / [tests](../../qwenbridge-server/src/test/java/io/qwenbridge/execution/provider/opensearch/client/OpenSearchClientTest.java) | Queries, mappings, response adaptation, retry/error behavior and timeouts exist. The local stack disables authentication; credentialed backend acceptance is not implemented/verified. |
| 25 | PostgreSQL and MongoDB Connectors | **missing** | No matching implementation or acceptance tests | Neither backend has a connector implementation, dependency, schema mapping or query-translation test suite. |
| 26 | Frontend-Neutral Search API | **partial** | [source](../../qwenbridge-server/src/main/java/io/qwenbridge/api/SearchAnalyzeController.java) / [tests](../../qwenbridge-server/src/test/java/io/qwenbridge/api/SearchAnalyzeControllerTest.java) | REST analyze, error contracts, IDs, health/version and typed SSE are UI-neutral. Public API still lacks a typed commerce filter/result contract and schema negotiation. |
| 27 | Java SDK and historical Spring Boot Starter | **implemented** | [source](../../qwenbridge-java-sdk/src/main/java/io/qwenbridge/sdk/QwenBridgeClient.java) / [tests](../../qwenbridge-java-sdk/src/test/java/io/qwenbridge/sdk/QwenBridgeClientTest.java) | Java SDK supports sync/async, retries and typed SSE; all modules target Java 25. Starter is intentionally removed and is not an outstanding requirement. Historical release notes are marked historical. |
| 28 | JavaScript Web Component | **missing** | No matching implementation or acceptance tests | TypeScript SDK is a client library. No customElements.define, browser widget, UI component contract or component tests. |
| 29 | Developer Playground | **missing** | No matching implementation or acceptance tests | Swagger/OpenAPI exists, but there is no playground displaying query, confidence, evidence and results. Earlier README and CI referenced absent playground files; those claims were removed. |
| 30 | Search Analytics and Evaluation | **partial** | [source](../../qwenbridge-server/src/main/java/io/qwenbridge/evaluation/metrics/RetrievalEvaluationMetrics.java) / [tests](../../qwenbridge-server/src/test/java/io/qwenbridge/evaluation/metrics/RetrievalEvaluationMetricsTest.java) | Offline Precision@K, Recall@K, reciprocal rank/MRR and nDCG benchmark tooling exist. Operational metrics are not click/query analytics; production relevance judgments and quality acceptance remain separate. |
| 31 | Plugin SDK Foundation | **partial** | [source](../../qwenbridge-server/src/main/java/io/qwenbridge/ai/provider/spi/AIProvider.java) / [tests](../../qwenbridge-server/src/test/java/io/qwenbridge/execution/provider/registry/DefaultSearchProviderRegistryTest.java) | AI/search provider interfaces are extension points. No general plugin manifest, loading API, compatibility contract or third-party plugin SDK acceptance. |
| 32 | Domain Pack Architecture | **missing** | No matching implementation or acceptance tests | No domain-pack type, manifest, dependency boundary or domain-specific pack discovery. |
| 33 | Connector Pack Architecture | **missing** | No matching implementation or acceptance tests | No connector-pack manifest, configuration contract, install lifecycle or upgrade semantics. StructuredExecutionLimits comments labelled stages 33+ concern input budgets only. |
| 34 | AI Provider Pack Architecture | **partial** | [source](../../qwenbridge-server/src/main/java/io/qwenbridge/ai/provider/spi/AIProvider.java) / [tests](../../qwenbridge-server/src/test/java/io/qwenbridge/ai/provider/ollama/client/OllamaClientReliabilityTest.java) | AI provider registry/resolver and Ollama implementation exist. No provider-pack manifest, packaging, install lifecycle or second provider acceptance. |
| 35 | Policy Pack Architecture | **missing** | No matching implementation or acceptance tests | Threat-rule YAML files are not installable policy packs. No pack contract, scoped policy selection or lifecycle. |
| 36 | Pack Registry and Lifecycle | **missing** | No matching implementation or acceptance tests | Provider bean registries do not install/activate/disable/upgrade packs. No pack lifecycle state machine, rollback, signature or dependency management. |
| 37 | Licensing and Entitlement Engine | **missing** | No matching implementation or acceptance tests | Apache repository license exists. No runtime license validation, entitlement evaluation, signing keys or feature enforcement. |
| 38 | Community, Professional and Enterprise Editions | **missing** | No matching implementation or acceptance tests | No edition model, capability matrix or tested feature gates. |
| 39 | QwenBridge Marketplace Foundation | **missing** | No matching implementation or acceptance tests | No marketplace metadata contract, publisher validation, package publication workflow or marketplace API. |
| 40 | Machine Output Parsing Foundation | **missing** | No matching implementation or acceptance tests | AI search-JSON parsing exists for search analysis only. No machine-output parser SPI, canonical input formats or Kubernetes adapter. |
| 41 | Typed Machine Event Model | **missing** | No matching implementation or acceptance tests | PipelineEvent represents search lifecycle events, not a canonical machine event/source/resource schema. |
| 42 | Machine-to-Human Explanation Engine | **missing** | No matching implementation or acceptance tests | ThreatExplanation renders threat findings for one query. No general machine-event explanation service with retained evidence. |
| 43 | Audience-Aware Explanation | **missing** | No matching implementation or acceptance tests | No developer/DevOps/manager/end-user audience model, rendering policy or audience acceptance tests. |
| 44 | Evidence and Source Attribution | **partial** | [source](../../qwenbridge-server/src/main/java/io/qwenbridge/threat/explanation/ThreatExplanation.java) / [tests](../../qwenbridge-server/src/test/java/io/qwenbridge/threat/explanation/ThreatExplanationTest.java) | Threat findings, grounded query spans and pipeline event timestamps provide local evidence. No machine-event evidence IDs, source/time attribution store or end-to-end citation contract. |
| 45 | Root-Cause Analysis Engine | **missing** | No matching implementation or acceptance tests | No ranked root-cause hypotheses, causal evidence model or root-cause evaluation dataset. |
| 46 | Event Correlation Engine | **missing** | No matching implementation or acceptance tests | Threat correlation combines rule findings from one input. It does not correlate machine events across services/resources/time. |
| 47 | Timeline Reconstruction | **missing** | No matching implementation or acceptance tests | Search pipeline events have order/timestamps. There is no machine-event timeline reconstruction or dependency/clock-skew handling. |
| 48 | Impact and Blast-Radius Analysis | **missing** | No matching implementation or acceptance tests | No service/resource dependency graph, impacted-user model, propagation analysis or uncertainty bounds. |
| 49 | Recommended Action Engine | **missing** | No matching implementation or acceptance tests | No action candidates, risk/success scoring or evidence-backed remediation ranking. Existing direct-answer executor contains placeholder output. |
| 50 | Human Feedback and Correction Loop | **missing** | No matching implementation or acceptance tests | No feedback endpoint/storage, correction lineage, reviewer identity or evaluation/correction loop. |

## Why recovered stage labels do not establish the roadmap

`StructuredExecutionLimits` uses comments such as “Stages 33–37” and “Stages 43–50”
for work budgets, field limits and evidence hygiene. Those are iterations of one
structured-query foundation, not implementations of connector packs, licensing,
Machine-to-Human or the corresponding product phases. The supplied caveat about
`qwenbridge-stages-14-to-50.patch` is consistent with the code evidence. That patch
file itself is not present in this checkout and has not been represented as inspected.
The former local `mnt/data/stage8-src` validator snapshot was removed during cleanup;
its two files duplicated the server sources apart from formatting.

## Concrete public-path limitation

`SearchAnalyzeController -> SearchPipeline -> PipelineEngine -> DecisionStep ->
DefaultExecutionEngine -> OpenSearchProvider` is the normal request path.
`StructuredSearchRuntimeGateway` has **no public caller**, defaults to disabled and
requires a caller-provided approval flag. It must not be confused with authenticated
authorization or mandatory constraint enforcement. `LogicalExecutionPlanner` rejects
structured bounds rather than running them through the ordinary execution plan.
A query such as “mouse under 50 EUR” can therefore return out-of-budget products
through the current public endpoint. Wiring typed constraints into every public
execution path is the first unfinished commerce acceptance requirement.

## Shopify

No Shopify connector, Shopify test or Shopify call was found in the current source
or inspected commit subjects. Any earlier Shopify sample client call cannot establish
that the application supports Shopify. A provider-specific call is appropriate in
an optional connector or an end-to-end connector test. Normal application startup,
search tests and Maven builds do not need a live Shopify account. Use local fixtures
for deterministic connector contracts and explicitly enabled, credentialed tests for
real-store compatibility when such a connector is implemented. No live Shopify
account was contacted during this audit.

## Fixes made during the audit

- Enforce JDK 25 in the parent reactor; select an installed JDK 25 in Bash entry points; move the SDK CI job to Java 25.
- Build a complete Quarkus fast-jar distribution in Docker and launch `quarkus-run.jar` on Java 25 as UID 65532. Include curl for the actual healthcheck.
- Replace the misleading Spring event-publisher class/package name with CDI; preserve Jakarta event behavior.
- Remove deleted starter expectations and CI jobs referencing absent integration, native and playground files. Historical release records remain explicitly historical.
- Add a reproducible local verification entry point and drift checks for reactor/npm/runtime/CI versions.
- Preserve failure exit codes, count skipped checks separately, stop runtime checks when prerequisites fail and preserve Docker volumes by default, isolate each verification index/cache prefix and refuse implicit index replacement.
- Correct k6 JavaScript syntax, remove its remote helper dependency, require assertion thresholds and recognize expected HTTP validation statuses.
- Reject nonempty uncompiled OpenSearch constraints, preserve score ordering above one, add a real query-term reranker and expose uncalibrated confidence signals; missing analysis no longer receives a fabricated high score.
- Fix the Quarkus leading-slash path mismatch that skipped abuse enforcement; reject known-length and chunked oversized bodies before provider invocation and commit SSE headers before the first event.
- Upgrade the formatter for JDK 25, apply the configured style, fix mutable response/evidence collections and preserve interruption during reranker fallback.

## Remaining implementation order

1. Finish typed commerce execution and identity authorization: public IR/schema contract, extraction quality, mandatory mapping/validation, supported backend filters and request isolation. Add price/negation/unsupported-field end-to-end tests.
2. Catalog ingestion/domain contracts, zero-result recovery, refinement and conversation state; then actual Elasticsearch/PostgreSQL/MongoDB connector contracts. Implement Shopify only if that provider is selected.
3. Browser Web Component, developer playground and real query/click analytics. Measure reranking and multilingual retrieval against judged data rather than source-file checks.
4. Define and implement plugin/pack manifests and lifecycle, then licensing, edition gates and marketplace metadata. Provider registries alone do not fulfill these requirements.
5. Implement Machine-to-Human phases 40–50 around a versioned event/evidence model, parser adapters, audience rendering and evaluated causality/feedback contracts.

Runtime and test execution outcomes, including the outstanding cold-cache latency threshold failure, are recorded in [verification results](verification-results.md).

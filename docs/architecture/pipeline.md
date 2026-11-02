# Pipeline (legacy v1)

The legacy v1 API (`POST /api/v1/search/analyze`, `GET /api/v1/search/stream/{requestId}`) runs
an ordered, typed pipeline in `io.qwenbridge.pipeline`. The v2 API runs on
`CommerceSearchService`; see [flows](flows.md#1-v2-search-end-to-end).

## Steps

`PipelineEngine` collects every `PipelineStep` bean (`@All`) and runs them in `order()` sequence:

| Order | Step | What it does |
|---|---|---|
| 10 | `LanguageStep` | Language analysis (`LanguageService`) |
| 15 | `NormalizationStep` | Threat and search normalization, typo correction (`TypoService`) |
| 20 | `ThreatStep` | Deterministic threat detection |
| 25 | `AIAnalysisStep` | One model analysis call: intent, rewrite, semantics, decision hints |
| 26 | `ModelSafetyStep` | Safety check on the model output |
| 27 | `TranslationStep` | Optional translation (`qwenbridge.translation.provider`) |
| 28 | `ExpansionStep` | Multilingual expansion (`ExpansionService`) |
| 30 | `IntentStep` | Intent from the analysis |
| 31 | `QueryIrStep` | QueryIR 1.1 |
| 32 | `ConstraintValidationStep` | Schema and grounding checks |
| 40 | `RewriteStep` | Rewrite from the analysis |
| 50 | `SemanticStep` | Semantic signals from the analysis |
| 70 | `PolicyStep` | Policy evaluation |
| 80 | `DecisionStep` | Decision and constraint questions |
| 90 | `ConfidenceStep` | Conservative confidence |

Each step reads and writes typed values in `ExecutionContext`, so steps never couple through
string keys. `IntentStep`, `RewriteStep`, `SemanticStep`, `DecisionStep` and `ConfidenceStep` take their
values from the single `AIAnalysisStep` result. QB-287
removed the former per-step AI services, which no step used.

## Execution and events

The execution engine (`io.qwenbridge.execution`) maps an `ExecutionPlan` to operations: direct
answer, keyword, vector or hybrid search, facets, rerank, return results. The search backend is
the in-memory provider unless `qwenbridge.search.backend=opensearch`.

Pipeline events are published independently of HTTP delivery. The SSE registry forwards them to the
stream for the request ID; the first event is `stream.connected`.

[Remediation proposal 2](remediation-proposals.md#2-serve-the-v1-api-from-the-core-engine)
describes serving v1 from the Core engine.

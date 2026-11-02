# ADR 0015: Universal QueryIR Contract (v1.0)

## Status

Accepted (QwenBridge 1.0, Section 1 Phase 6: QB-036 – QB-042)

## Context

The repository had six overlapping query models (`intelligence.ir.QueryIR`,
`CommerceContracts.Plan`, `ExtractedQuery`, `SearchAnalysis`,
`SemanticAnalysis`, `LogicalQueryPlan`) and four operator enums.

The only model named QueryIR represented price upper bounds only, and it had
an integer version with no compatibility rules. Phases 3–5 (translation,
intent, extraction) and every later section need one canonical contract to
write into.

## Decision

`io.qwenbridge.core.queryir.QueryIR` in `qwenbridge-core` is the canonical
intermediate representation.

### Shape

The top level has these fields: `version`, `queryId`, `originalText`,
`language`, `intents`, `entities`, `constraints`, `semanticSignals`,
`retrievalHints`, `subqueries`, `extensions`.

- **Domain neutrality.** No field is domain-specific. Entity types and
  constraint fields are schema-defined names (`price`, `memory_usage`,
  `kubernetes.pod`), not Core enums. Domain payloads go in namespaced
  `extensions`, the only place the Core does not interpret. A contract test
  fails if the top-level shape changes or if any component name mentions a
  domain or backend.
- **Constraints.**
  - Each has an explicit `HARD`/`SOFT` strength and a recorded `reason`.
  - Each has a provenance (`USER_TEXT`, `CONTEXT`, `INFERRED`,
    `DOMAIN_RULE`). User-stated constraints require a text span, and inferred
    constraints can never be HARD.
  - Negation lives in the operator (`NE`, `NOT_IN`, `NOT_CONTAINS`,
    `NOT_EXISTS`), so an exclusion cannot turn into a preference.
- **Typed values.**
  - `text`, `number`, `boolean`.
  - `quantity` (value + unit).
  - `money` (BigDecimal amount + optional ISO 4217 currency). An ambiguous
    currency word such as "krona" leaves the currency empty.
  - `temporal` (instant, date, duration, or a relative past window that is
    marked unresolved without an explicit reference time).
  - `range`, `list`.
- **Evidence.** Every span must equal `originalText.substring(start, end)`.
- **Confidence.** Uses the shared `Confidence` type (`UNKNOWN`,
  `DETERMINISTIC`, `HEURISTIC`, `MODEL_SCORE`, `MODEL_DECISION`). Only
  `MODEL_SCORE` carries a number, which is never presented as a calibrated
  probability. The language-specific `DetectionConfidence` was folded into
  this type, so one confidence contract exists.
- **Decomposition.**
  - Subqueries reference parent intents and constraints by id.
  - Constraints can be scoped to a subquery.
  - `dependsOn` must be acyclic.
- **Retrieval hints.** Overlapping, backend-neutral modes (`EXACT`,
  `LEXICAL`, `SEMANTIC`, `STRUCTURED`); lexical plus semantic means hybrid.
  Cross-language intent and content languages are recorded too. No search
  DSL, SQL or document-store operator appears anywhere.

### Versioning (`IrVersion`, `major.minor`, current `1.0`)

- Minor versions only add optional fields. A reader at `1.n` reads every
  `1.m` with `m <= n`.
- A newer minor or a different major is rejected before any other field is
  interpreted. Silently dropping fields whose meaning is unknown could change
  the query.
- Unknown fields are rejected everywhere except inside `extensions`.
- **1.1 (QB-291, ADR 0054)** adds the optional `correction` object (typo correction). Readers
  accept 1.0 and 1.1. Documents without a correction are still written as 1.0.
- **1.1 also adds `expansion` (QB-286, ADR 0055):** multilingual expansion metadata, optional. Its
  minor version stays 1.1 because 1.1 has not been released.

### Serialization (`QueryIrJson`)

- Strict reading. Exact `BigDecimal` numbers (scale preserved). JSON nesting
  limited to 32.
- Failures are typed `IrViolation`s (code plus JSON-pointer path); user text
  is never echoed.
- Writing is deterministic, so golden fixtures round-trip byte-stable.

### Dependency

`qwenbridge-core` gains one runtime dependency: `jackson-databind`. It is a
domain-neutral serialization library that the server, the Java SDK and Quarkus
already use. Letting the contract own its wire format avoids a second,
divergent mapping. Infrastructure clients (Redis, PostgreSQL, MongoDB,
OpenSearch, Elasticsearch), Quarkus and Commerce remain forbidden in Core.

## Evidence

- Golden fixtures in `qwenbridge-core/src/test/resources/queryir/v1.0/`:
  Commerce, DevOps, Security, Observability, Persian with an ambiguous
  currency, and multi-step decomposition.
- Tests cover round trips plus version, unknown-field, span, reference,
  cycle, operator/value, range, provenance and limit rules.
- A mutation check confirmed the tests fail when span or cycle validation is
  disabled.

## Consequences

- Later phases write into this contract:
  - Phase 4: intents and decomposition.
  - Phase 5: entities and constraints.
  - Phase 7: schema-validated fields.
  - Phase 15: the planner reads retrieval hints.
- The legacy models are not deleted. They are migrated when their phase is
  implemented (extraction in Phase 5; the Commerce `Plan` becomes a Commerce
  extension in Sections 2–3).
- The v2 Commerce API still publishes `CommerceContracts.Plan` as `queryIR`.
  Changing that wire shape is a Section 2/3 compatibility decision.

# ADR 0028: Connectors Implement the Unified Search SPI and Translate the Neutral Filter

## Status

Accepted (QwenBridge 1.0, Section 2 Phase 15 part 2: QB-107 connector side, QB-100 connector side)


> **QB-287 (ADR 0058):** superseded in part. The Elasticsearch, PostgreSQL and MongoDB connectors described here were removed from the repository; the generic contracts (SearchProvider, Connector SPI) remain the extension point for future adapters.

## Context

The Elasticsearch, PostgreSQL and MongoDB connectors compiled the Commerce `Plan`
directly into their own query languages. Their not-equal rule also differed from
the neutral `Filter` semantics: they required the field to exist, whereas the
neutral semantics treat a missing field as satisfying NE.

## Decision

1. **Translators.** `NeutralFilterTranslator` translates the `Filter` AST for
   each backend, with the tenant always added by the translator:

   | Backend | Translation |
   |---|---|
   | Elasticsearch / OpenSearch | `bool` / `filter` / `should` / `must_not` DSL |
   | PostgreSQL | Parameterized jsonb SQL |
   | MongoDB | Typed BSON |

   Every translation follows `FilterEvaluator`:
   - **Missing fields.** A missing field fails positive comparisons and
     satisfies NE and NOT.
   - **Type mismatches** never match. In SQL every leaf is wrapped in
     `COALESCE(…, false)`, and numeric casts are guarded by `jsonb_typeof`, so
     mistyped data can't raise an error.
   - **Ordering** applies to numbers only.
   - **No user value in query text.** Values are always bound parameters or
     typed leaves (QB-059).
2. **Adapters.** The `ElasticsearchBackend`, `PostgreSQLBackend` and
   `MongoDBBackend` classes now also implement the Core `SearchProvider` SPI,
   alongside the Commerce interface.
   - Each declares its keyword capabilities: all filter features, topK up to
     100.
   - Unsupported requests, unconfigured backends, cancellation, timeouts and
     failures are typed outcomes.
   - Results return the stored `documentId`, never backend-internal ids.
   - Fields are typed for re-validation.
   - Scores are backend-native retrieval scores.
3. **Live conformance.** The test is opt-in and runs with the Table 1 backends
   in the master verification. Each connector must return exactly the document
   set that `FilterEvaluator` selects from the same data, for 12 filters:
   equality, NE over missing fields, numeric bounds, ordering on text, booleans,
   IN, NOT EXISTS, OR, and combinations. It also checks keyword narrowing within
   the tenant and typed outcomes.

   Running it found and fixed one defect before release: Elasticsearch results
   returned internal `_id` digests instead of document ids.

## Consequences

- The planner can choose any of these connectors through capabilities alone.
- OpenSearch runs through the separate legacy provider stack. It adopts the same
  translator and SPI when Commerce moves onto the planner (QB-085 – QB-091).
  Until then QB-107 stays PARTIAL for OpenSearch.
- Vector search through the connectors (ES knn, OS knn) is not yet behind the
  unified SPI. The in-memory vector index serves VECTOR plans until then.

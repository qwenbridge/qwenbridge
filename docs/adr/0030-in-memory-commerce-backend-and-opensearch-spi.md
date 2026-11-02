# ADR 0030: In-Memory Commerce Backend, OpenSearch on the Unified SPI, Open Backend Registry

## Status

Accepted (QwenBridge 1.0, Section 2 Phase 13 part 1: QB-091, QB-085 backend
registry, QB-107 completed for OpenSearch)


> **QB-287 (ADR 0058):** superseded in part. The Elasticsearch, PostgreSQL and MongoDB backends described here were removed from the repository; the generic contracts (CommerceBackend, SearchProvider) remain the extension point for future adapters.

## Context

Before this change:

- Commerce search always needed a search service. The only in-process provider
  was a legacy three-product demo stub, and the Commerce API tests mocked the
  OpenSearch client (audit, `CommerceApiTest`).
- OpenSearch, the default backend, was the only connector not on the unified
  search SPI.
- The Commerce request contract hard-coded the four backend names, so adding a
  connector meant changing the public contract.
- Requesting a mode the backend lacks returned 500, although the API document
  promises 422. The JAX-RS status enum has no 422, and the error mapper turned
  every unknown status into a server error.

## Decision

1. **In-memory backend (QB-091).** `MemoryCommerceBackend` (id `memory`) is a
   Commerce backend over the Core `InMemoryKeywordIndex`.
   - **Ranking.** BM25 over title, brand, category and description. A query
     without terms returns all documents.
   - **Plan.** The plan is applied with `CommerceCompiler.matches`, the
     predicate the result check uses, so the backend cannot return a document
     that the check would reject.
   - **Unified SPI.** The same documents answer neutral `Filter` requests with
     the reference semantics.
   - **Scope.** Keyword mode only; vector and hybrid modes are declared
     unsupported rather than approximated. Up to 100,000 documents per
     instance; data lives in process memory, is lost on restart and is not
     shared between instances.
   - **Selection.** Set `qwenbridge.commerce.backend=memory`, or pass
     `backend: "memory"` in a request. It needs only the base `search`
     entitlement.
2. **OpenSearch on the unified SPI (QB-107).** `OpenSearchBackend` implements
   `SearchProvider` through the same neutral-filter DSL as Elasticsearch. The
   two connectors now share one keyword query builder,
   `ConnectorProviders.keywordPayload`, because the seed mapping and the
   Elasticsearch mapping have the same shape.
3. **Open registry (QB-085, part).** The request contract only checks the
   backend name's syntax (`[a-z][a-z0-9-]{0,31}`). `CommerceBackends` resolves
   the name: an unknown backend returns 400, as before.
4. **422 for unsupported modes.** The error mapper keeps any 4xx status that
   the enum lacks: 422 becomes `VALIDATION_ERROR` "Unprocessable Content"
   instead of 500.
5. **Tests.**
   - **Synthetic catalog (offline QuarkusTest).** Ten products for tenant A are
     ingested through the Commerce pack's canonical pipeline, plus one cloned
     product for tenant B. The full `/api/v2/search` API then returns exactly
     the two products that satisfy all seven constraints. The test also covers:
     - tenant isolation;
     - stable cursor paging that covers the result set once;
     - explicit and unknown backends;
     - 422 for VECTOR mode;
     - OpenSearch never being called.
   - **Unit:** Commerce and neutral semantics side by side.
   - **Live OpenSearch conformance.** Gated by `QWENBRIDGE_RUN_OPENSEARCH_IT`.
     It runs the production `OpenSearchBackend` against a fresh, uniquely named
     index with the seed mapping, using the 12 filters and documents of the
     other connectors' conformance test. It returns exactly the reference sets
     and stays within the tenant. The index is deleted afterwards; no existing
     index is touched.

## Consequences

- **QB-107 is complete.** OpenSearch, Elasticsearch, PostgreSQL, MongoDB and
  the in-memory backend all implement the unified SPI with typed outcomes.
- **Two NE semantics.** The Commerce v2 plan semantics (NE requires the field)
  and the neutral semantics (a missing field satisfies NE) still differ. Both
  are tested. They are reconciled when Commerce executes through the planner
  (QB-086, QB-087), which is Batch 17b.
- **Client-visible changes:**
  - A request for an unsupported mode now returns 422 instead of 500.
  - `GET /api/v2/backends` lists `memory`.
- **Not done here.** The default backend stays OpenSearch. The legacy v1 demo
  `InMemorySearchProvider` is left as it is; it belongs to the v1 pipeline, not
  to Commerce.

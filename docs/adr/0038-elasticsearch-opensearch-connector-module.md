# ADR 0038: Optional Elasticsearch / OpenSearch Connector Module

## Status

Accepted (QwenBridge 1.0, Section 3 Phases 23 and 24: QB-160 – QB-171)


> **QB-287 (ADR 0058):** superseded in part. The `qwenbridge-connector-elastic` module and the built-in Elasticsearch backend (OpenSearch stays as the built-in optional backend; its filter translation moved into the server) described here were removed from the repository; the generic contracts (SearchProvider, Connector SPI) remain the extension point for future adapters.

## Context

The audit (QB-160 – QB-171) found the following:

- **Location.** The Elasticsearch and OpenSearch adapters lived in the server.
  OpenSearch was the server's default backend, and Commerce code built its
  queries.
- **TLS.** There was no custom CA support.
- **Hybrid search** was a native `bool should` mix of BM25 and kNN scores.
- **Validation.** No index mapping or dimension validation existed.
- **Tests.** There were no outage or malformed-response tests.

## Decision

1. **Optional module (QB-160, QB-166).** `qwenbridge-connector-elastic` is a new
   Maven module.
   - **Dependencies:** only `qwenbridge-core` (Jackson) and the JDK HTTP client.
   - **Connector:** `ElasticConnector` (dialect ELASTICSEARCH or OPENSEARCH)
     implements the Core `Connector` SPI, exposes keyword and vector
     `SearchProvider`s, and declares READ access.
   - **Optional at runtime:** Core starts without either engine; the Section 1
     gate starts the server with OpenSearch unreachable.
   - **Server wiring:**
     - the Elasticsearch backend's unified-SPI search is served by this module;
     - the server's Elasticsearch/OpenSearch filter translation delegates to
       it, so there is one implementation.
2. **Authentication and TLS (QB-161).**
   - **Authentication** is Basic (username plus a `password` secret
     reference) or Bearer (an `api-token` reference), never both.
   - **Endpoint rules:** credentials in the URL are refused; redirects are not
     followed.
   - **TLS:** a `ca-certificate` PEM makes TLS trust exactly that CA, and host
     names are always verified.
3. **Keyword search (QB-162, QB-167).**
   - **Query:** BM25 `multi_match` over the configured text fields (match-all
     for structured-only queries), plus the neutral filter as typed DSL leaves
     with the tenant term.
   - **Exact fields** are derived from the live index mapping: `keyword` fields
     directly, `text` fields through their `.keyword` subfield.
   - **Order** is stable: score, then document id.
4. **Vector and hybrid search (QB-163, QB-168, QB-169).**
   - **kNN per dialect**, always with the neutral filter applied inside the kNN
     search:
     - Elasticsearch: top-level `knn` with `num_candidates`;
     - OpenSearch: `query.knn`.
   - **Embedding space.** The vector provider declares the configured embedding
     space. A query vector from another space is refused before any request is
     sent.
   - **Hybrid** is Core's RRF fusion over the two providers: two separate
     requests, with ranks kept per source. BM25 and vector scores are never
     added.
5. **Mapping and dimension validation (QB-170).** Starting reads
   `/<index>/_mapping`.
   - **Missing index or no access:** the start fails (FAILED with reason).
   - **Mismatched vector field:** a vector field of the wrong type or dimension
     disables vector search and leaves the connector DEGRADED with the reason
     (for example "dimension 8, the embedding space has 3"). Keyword search
     keeps working, and semantic retrieval is reported unavailable instead of
     silently falling back.
6. **Result mapping (QB-164).** Each result uses the stored `documentId`, and
   the engine `_id` only when none is stored.
   - **Scores** are labelled `BM25` or `VECTOR_SIMILARITY` with the source
     `<connector>:<index>`.
   - **Fields** are typed (exact decimals, booleans, text lists).
7. **Failures (QB-171).** Every failure is a typed outcome, and no response body
   or credential reaches a message:

   | Failure | Outcome |
   |---|---|
   | HTTP 5xx | UNAVAILABLE |
   | HTTP 4xx | INVALID_REQUEST |
   | 401/403 at start | FAILED (UNAUTHORIZED) |
   | Malformed or oversized response | UNAVAILABLE (MALFORMED_RESPONSE) |
   | Timeout | TIMEOUT |
   | Connection refused | UNAVAILABLE |

8. **Tests (QB-165).**
   - **Offline (5),** against a scripted HTTP endpoint:
     - request shape, authentication and canonical results;
     - both kNN dialects;
     - refusal of a foreign embedding space without a request;
     - a dimension mismatch giving DEGRADED;
     - hybrid as two requests fused in Core;
     - every failure class, including an outage;
     - start failures.
   - **Live**, each in a fresh index the test creates and deletes, covering
     keyword with mapping-derived exact fields, kNN with the filter inside it,
     tenant isolation and a dimension mismatch:
     - Elasticsearch 8 (Table 1, Basic auth);
     - OpenSearch;
     - OpenSearch over TLS with a private CA. Without the CA the start fails.
   - **Regression:** the existing 12-filter conformance on Elasticsearch passes
     through the module.

## Consequences

- **The OpenSearch Commerce path** keeps the Quarkus REST client, so deployments
  keep their Quarkus TLS registry configuration; the server's OpenSearch kNN for
  1.0 is unchanged. Moving it onto this module needs its TLS settings expressed
  as connector settings.
- **Legacy compilation.** The apiVersion 2 Commerce compilation
  (`CommerceCompiler` to DSL) for Elasticsearch and OpenSearch stays in the
  server until v2 runs on the planner.

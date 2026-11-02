# ADR 0026: Hybrid Retrieval, Rank Fusion, Index Lifecycle and Labelled Fallback

## Status

Accepted (QwenBridge 1.0, Section 2 Phase 16 part 2: QB-112 – QB-116)

## Context

Hybrid retrieval existed only as backend-native queries (OpenSearch bool+knn,
Elasticsearch knn+query). There was no local fusion, no provenance and no index
lifecycle. When embeddings were unavailable, OpenSearch fell back silently: the
results were lexical but were still labelled hybrid (audit,
`OpenSearchQueryFactory:140-159`).

## Decision

1. **Hybrid retriever (QB-112).** `HybridRetriever` runs keyword and vector
   retrieval concurrently, with the same tenant and filter. Each side
   over-fetches candidates, and the results are fused.
2. **Rank fusion (QB-113).** `RankFusion.rrf` scores each document as
   `Σ 1/(k + rank)` with `k = 60` by default.
   - Fusion uses ranks only, so incomparable scores (BM25 and cosine) are never
     added together.
   - Duplicates collapse into one `FusedDocument` that keeps every source's
     rank and its original `RetrievalScore` unchanged.
   - Ties break by the best single-source rank, then by id, so the order does
     not depend on the order of the sources.
   - The fused score is a `HYBRID_FUSION` retrieval score.
3. **Index lifecycle (QB-114).** `ManagedVectorIndex` provides upsert (embed,
   then insert), delete, rebuild and close.
   - A rebuild embeds every source document into a fresh index in the current
     embedding space. It swaps the new index in atomically only if every batch
     succeeded.
   - Searches keep using the old index during a rebuild, and a failed rebuild
     leaves it untouched.
   - Only one rebuild runs at a time. After close, searches answer UNAVAILABLE
     and writes are refused.
4. **Labelled fallback (QB-115).** `HybridResult` reports `requested` and
   `executed` separately (HYBRID, KEYWORD_ONLY, VECTOR_ONLY or NONE), plus typed
   `degradations`:

   | Degradation | When |
   |---|---|
   | `EMBEDDING_UNAVAILABLE` | No provider, unavailable or timed out |
   | `REINDEX_REQUIRED` | The index was built in another embedding space |
   | `PROVIDER_UNSUPPORTED` | A provider cannot serve the request |
   | `PROVIDER_FAILED` | A provider call failed |

   Lexical results are therefore never presented as semantic. If both sides
   fail, the result is NONE with both reasons and no documents.
5. **Tests with real vectors (QB-116).**
   - **Offline:** a deterministic character-trigram hashing embedder produces
     real vectors over a synthetic multilingual catalog (test fixture only;
     not shipped). The tests cover:
     - RRF arithmetic and provenance;
     - typo recovery by the vector side ("wireles headphone");
     - agreement between both sides on exact queries;
     - filters and tenants applied on both sides;
     - every fallback;
     - rebuild under concurrent searches, a failed rebuild, delete and close.
   - **Live (opt-in, `QWENBRIDGE_RUN_OLLAMA_IT`):** the server's embedding stage
     with the real configured model (bge-m3) answers English, Persian and
     Swedish queries over one catalog.

## Consequences

- The adaptive planner (QB-099 – QB-106) decides when hybrid retrieval is worth
  running and reads `executed` and `degradations` back into the response
  contract (QB-090).
- The connectors keep their backend-native hybrid queries until they implement
  the unified SPI. They must then report degradation in the same way.

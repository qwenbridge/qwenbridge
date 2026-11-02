# ADR 0025: Unified Search Provider SPI, In-Memory Indexes and Embedding Spaces

## Status

Accepted (QwenBridge 1.0, Section 2 Phase 16 part 1: QB-107 – QB-111; filter AST of QB-100)

## Context

Before this change:

- Retrieval had two provider interfaces: the server's `SearchProvider` and
  Commerce's `CommerceBackend`.
- The in-memory provider was a three-product demo stub that refused filters.
- Embeddings went through the chat SPI (`AIProvider.embed`), with no model
  metadata.
- Vector dimensions were hard-coded in connector mappings.

## Decision

Core holds the retrieval primitives. They have no dependencies, so the default
deployment needs no search service.

1. **Filter AST (QB-100, part).** `core.retrieval.filter.Filter` is a sealed
   tree: `All`, `And`, `Or`, `Not`, `Compare` (EQ/NE/LT/LTE/GT/GTE), `In`,
   `Contains` and `Exists`. Values are typed `FieldValue`s, and numbers are
   exact decimals.
   - `FilterEvaluator` is the reference semantics. A missing field fails a
     positive comparison and satisfies NE and NOT, so exclusions keep documents
     that lack the field.
   - `Filter.features()` lists what a provider must support.
2. **Unified SPI (QB-107).** `SearchProvider` has `id`, `capabilities` and
   `search(SearchRequest, CancellationToken)`.
   - **Tenant is mandatory.** The request tenant is not a filter the caller can
     omit.
   - **Capabilities** declare modes, filter features, the embedding space,
     maxTopK and cancellation. `unmet()` names the first requirement a request
     needs that the provider lacks.
   - **Outcomes** are a sealed `SearchOutcome`: `Success`, `Unsupported` or
     `Failed` (TIMEOUT, CANCELLED, UNAVAILABLE, INVALID_REQUEST). Providers
     never degrade silently.
   - **Scores** are `RetrievalScore`s, never confidence.
3. **In-memory keyword index (QB-108).**
   - Tokenization is search normalization plus lower case, split on letter and
     digit runs in any script.
   - Scoring is BM25 (k1 = 1.2, b = 0.75). Term statistics are kept per tenant,
     so one tenant's documents cannot change another tenant's ranking.
   - Results apply the typed filters and are ranked by score, with the id as a
     deterministic tie-break.
   - The index is bounded (documents and topK), honours cancellation and the
     deadline, and uses read/write locking.
4. **In-memory vector index (QB-109).** Exact cosine similarity over
   unit-normalized vectors.
   - Dimensions are validated. Vectors must be finite; a zero query vector is
     rejected.
   - Reads run concurrently; writes are exclusive. Tenants are isolated.
   - The index has an id tie-break and is bounded.
5. **Embedding SPI (QB-110).** `EmbeddingProvider` (`id`, `EmbeddingModel`,
   batch `embed`) is separate from chat. `EmbeddingStage`:
   - checks the batch size and input size;
   - enforces the timeout;
   - turns exceptions into UNAVAILABLE;
   - rejects output with the wrong count, dimensions, space or non-finite
     values;
   - answers NO_PROVIDER when no provider is configured.

   The server exposes the AI service's embedding endpoint as
   `AIServiceEmbeddingProvider`, configured with
   `qwenbridge.embedding.{enabled,model-version,dimensions,timeout}`.
6. **Embedding spaces (QB-111).** `EmbeddingSpace` is the model, version and
   dimensions.
   - Query vectors (`QueryVector`) and indexes carry their space. Inserting or
     querying with another space is rejected, so vectors are never compared
     across spaces.
   - `IndexCompatibility` names the reindex reasons: MODEL_CHANGED,
     VERSION_CHANGED, DIMENSIONS_CHANGED.

## Consequences

- Hybrid retrieval, RRF, index lifecycle and the labelled fallback (QB-112 –
  QB-116) build on these types.
- Existing connectors (OpenSearch, Elasticsearch, PostgreSQL, MongoDB) adopt
  the unified SPI when the adaptive planner selects providers (QB-099 –
  QB-106).
- The legacy demo stub is replaced as the default backend when the Commerce
  layer moves onto the in-memory indexes (QB-091).

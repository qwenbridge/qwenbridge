# Retrieval Verification

QwenBridge V6 verifies retrieval behavior through an explicit OpenSearch query contract and a deterministic seed corpus.

## Search Modes

| Mode | Required inputs | OpenSearch query shape |
| --- | --- | --- |
| `KEYWORD` | query text | `multi_match` over `title`, `brand`, `category`, and `description` |
| `VECTOR` / `SEMANTIC` | query text + embedding vector | `knn` over the `embedding` field |
| `HYBRID` | query text + embedding vector | portable `bool.should` containing `multi_match` and `knn` subqueries |

When vector or hybrid mode is requested without an embedding, the provider falls back to keyword search instead of sending an invalid vector query.

QwenBridge V6 intentionally does not use the raw OpenSearch `hybrid` query. Runtime verification showed duplicate hits and unstable negative scores. The V6 launch contract therefore uses a portable `bool.should` hybrid query with `minimum_should_match=1`.

## Vector Index Contract

The seed script creates an OpenSearch index with:

- `index.knn=true`
- text fields for `title`, `brand`, and `description`
- keyword field for `category`
- `embedding` as a `knn_vector`
- BGE-M3 default dimension: `1024`
- HNSW/Lucene cosine similarity

## Seed Corpus

`scripts/opensearch-seed.sh` creates a small deterministic product corpus and generates embeddings through Ollama using BGE-M3.

Required local services:

    ollama serve
    ollama pull bge-m3
    docker compose up -d qwenbridge-opensearch

Seed command:

    OPENSEARCH_URL=http://localhost:9200 \
    OLLAMA_URL=http://localhost:11434 \
    QWENBRIDGE_EMBEDDING_MODEL=bge-m3 \
    ./scripts/opensearch-seed.sh

## Manual Verification

Keyword search:

    curl -s http://localhost:9200/qwenbridge-products/_search \
      -H 'Content-Type: application/json' \
      -d '{
        "query": {
          "multi_match": {
            "query": "gaming mouse",
            "fields": ["title^3", "brand^2", "category", "description"]
          }
        },
        "size": 3
      }'

Vector verification requires creating the query embedding first through Ollama and then passing it to a `knn` query against the `embedding` field.

Hybrid verification:

    EMBEDDING="$(curl -fsS http://localhost:11434/api/embed \
      -H 'Content-Type: application/json' \
      -d '{"model":"bge-m3","input":"gaming mouse razer esports"}' \
      | jq -c '.embeddings[0]')"

    curl -fsS "http://localhost:9200/qwenbridge-products/_search?pretty" \
      -H 'Content-Type: application/json' \
      -d "{
        \"size\": 3,
        \"query\": {
          \"bool\": {
            \"should\": [
              {
                \"multi_match\": {
                  \"query\": \"razer gaming mouse\",
                  \"fields\": [\"title^3\", \"brand^2\", \"category\", \"description\"]
                }
              },
              {
                \"knn\": {
                  \"embedding\": {
                    \"vector\": $EMBEDDING,
                    \"k\": 3
                  }
                }
              }
            ],
            \"minimum_should_match\": 1
          }
        }
      }"

Expected first result:

    product-5 / Razer DeathAdder V3

Optional real integration test:

    QWENBRIDGE_RUN_OPENSEARCH_IT=true \
    mvn -Dtest=OpenSearchHybridRetrievalIntegrationTest test

## V6 Scope

V6 freezes the retrieval query contract and seed workflow.

### Ranking Policy

- Keyword ranking uses OpenSearch `multi_match` field boosts.
- Vector ranking uses OpenSearch `knn_vector` cosine similarity.
- Hybrid ranking uses portable `bool.should` score combination.
- Raw OpenSearch `hybrid` query is prohibited in V6.

### Reranking Policy

- Rerank execution remains part of the execution model.
- Hosted or cross-encoder reranking is deferred.
- V6 does not expose reranker-specific scores publicly.

Hosted reranking, advanced score normalization, and production-grade OpenSearch search pipelines are deferred until the provider set expands beyond the initial Ollama/OpenSearch launch.

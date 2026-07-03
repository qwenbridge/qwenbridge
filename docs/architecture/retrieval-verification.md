# Retrieval Verification

QwenBridge V6 verifies retrieval behavior through an explicit OpenSearch query
contract and a deterministic seed corpus.

## Search Modes

The OpenSearch provider supports three retrieval shapes:

| Mode | Required inputs | OpenSearch query shape |
| --- | --- | --- |
| `KEYWORD` | query text | `multi_match` over `title`, `brand`, `category`, and `description` |
| `VECTOR` / `SEMANTIC` | query text + embedding vector | `knn` over the `embedding` field |
| `HYBRID` | query text + embedding vector | `hybrid` query containing keyword and vector subqueries |

When vector or hybrid mode is requested without an embedding, the provider
falls back to keyword search instead of sending an invalid vector query.

## Vector Index Contract

The seed script creates an OpenSearch index with:

- `index.knn=true`
- text fields for `title`, `brand`, and `description`
- keyword field for `category`
- `embedding` as a `knn_vector`
- BGE-M3 default dimension: `1024`
- HNSW/Lucene cosine similarity

## Seed Corpus

`scripts/opensearch-seed.sh` creates a small deterministic product corpus and
generates embeddings through Ollama using BGE-M3.

Required local services:

```bash
ollama serve
ollama pull bge-m3
docker compose up -d qwenbridge-opensearch
```

Seed command:

```bash
OPENSEARCH_URL=http://localhost:9200 \
OLLAMA_URL=http://localhost:11434 \
QWENBRIDGE_EMBEDDING_MODEL=bge-m3 \
./scripts/opensearch-seed.sh
```

## Manual Verification

Keyword search:

```bash
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
```

Vector verification requires creating the query embedding first through Ollama
and then passing it to a `knn` query against the `embedding` field.

## V6 Scope

V6 freezes the retrieval query contract and seed workflow. Hosted reranking,
advanced score normalization, and production-grade search pipelines are deferred
until the provider set expands beyond the initial Ollama/OpenSearch launch.

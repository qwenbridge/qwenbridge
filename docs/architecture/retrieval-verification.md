# Retrieval Verification

Retrieval verification ensures that QwenBridge can execute search plans through provider-neutral contracts and return stable, ranked results.

## Verified areas

- search provider resolution
- OpenSearch request mapping
- OpenSearch response mapping
- keyword search
- vector search
- hybrid search
- ranking
- reranking fallback
- execution-engine integration
- benchmark evaluation

## Provider boundary

The execution engine talks to `SearchProvider`, not directly to OpenSearch. OpenSearch-specific DTOs and query construction remain inside the OpenSearch provider package.

## Verification commands

```bash
mvn clean verify
bash scripts/verify-release.sh
```

For local OpenSearch-backed checks, start dependencies first:

```bash
docker compose up -d
bash scripts/opensearch-seed.sh
```

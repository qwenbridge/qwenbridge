# Troubleshooting

Each entry gives the symptom, the cause and the fix. Error responses carry `code`, `category` and
`retryable`. Server logs carry the same request ID and trace ID as the response.

## Startup

### `UnsupportedClassVersionError` or "release version 25 not supported"
**Cause:** the build or runtime JDK is older than 25.
**Fix:** run `java -version` and `mvn -version`; both must report 25. Use
`source scripts/verification/java-env.sh && configure_java25`.

### Every request returns 400 "Authentication requires configured principals"
**Cause:** the packaged application runs the `prod` profile, which requires API keys. The identity
configuration is checked on first use, so the server starts but refuses every request.
**Fix:** set `QWENBRIDGE_SECURITY_API_PRINCIPALS` to a JSON array of principals; each key must be
at least 24 characters. For a local trial only, set
`QWENBRIDGE_SECURITY_AUTHENTICATION_REQUIRED=false`. `ProductionConfigurationValidator` lists the
other settings it rejects.

### "Unknown backend" for `elasticsearch`, `postgresql` or `mongodb`
**Cause:** QB-287 removed these connectors. The registered backends are `memory` and `opensearch`.
**Fix:** set `QWENBRIDGE_COMMERCE_BACKEND` to `memory` (the default) or `opensearch`. To use
another data source, implement it as a connector pack; see [packs](../architecture/packs.md).

### Port 8080 already in use
**Fix:** set `QUARKUS_HTTP_PORT`.

## Dependencies

### Readiness shows `redis: DEGRADED (unavailable)`
**This is expected without Redis.** Rate limits fall back to memory; the analysis cache is
skipped. Only catalog synchronization needs Redis. To silence the cache and the distributed rate
limits, set `QWENBRIDGE_ANALYSIS_CACHE_ENABLED=false` and
`QWENBRIDGE_ABUSE_PROTECTION_REDIS_ENABLED=false`.

### Catalog sync returns 503
The error code is `DEPENDENCY_UNAVAILABLE`, with the message "Catalog synchronization needs Redis
for its checkpoint and lock".
**Cause:** `POST /api/v2/catalog/sync` keeps its lock and cursor in Redis.
**Fix:** start Redis (`docker compose up -d qwenbridge-redis`) and point
`QWENBRIDGE_ANALYSIS_CACHE_REDIS_HOST` at it. Static catalogs can be loaded without Redis through
`QWENBRIDGE_DEMO_DATASET_FILES`. A Redis-free design is
[remediation proposal 3](../architecture/remediation-proposals.md#3-catalog-synchronization-without-redis).

### Catalog sync returns 409
**Cause:** another run holds the tenant's lock. A lock expires 30 minutes after its last renewal.
**Fix:** wait for the other run.

### `opensearch` appears in readiness, or does not
OpenSearch is probed only when `QWENBRIDGE_SEARCH_BACKEND` or `QWENBRIDGE_COMMERCE_BACKEND` is
`opensearch` (QB-287). If you selected it and it shows `DEGRADED`, check
`QWENBRIDGE_SEARCH_OPENSEARCH_BASE_URL` and run `docker compose --profile opensearch up -d`.

### Model calls time out, or `ollama: DEGRADED`
**Cause:** Ollama is unreachable, or the models are missing.
**Fix:**
1. Run `curl http://localhost:11434/api/tags` and check that `qwen2.5` and `bge-m3` are listed.
2. Start `qwenbridge-ollama-init` to pull them.
3. If the model is slow on this machine, raise `QWENBRIDGE_AI_OLLAMA_READ_TIMEOUT`.

## Search results

### A search returns no results but the product exists
**Cause:** a hard constraint excludes it. For example, the product is out of stock, or its price
is in another currency.
**Fix:**
- Inspect `queryIR.constraints` and `retrieval.removedByValidation` in the response.
- `GET /api/v2/search/{id}/recovery` proposes explicit relaxations.

### `VECTOR` or `HYBRID` mode returns 422
**Cause:** the in-process backend refuses an explicit vector or hybrid mode on the v2 path without
the planned contract.
**Fix:** send `"contract":"commerce-search/1.0"` and let the planner choose. Also set
`QWENBRIDGE_DEMO_EMBED=true` so that documents have embeddings.

### A word is "corrected" into another language
**Cause:** typo correction never changes a word that any enabled language knows. If the word is
missing from every enabled dictionary, it can be corrected.
**Fix:** add the word to the dictionary in `QWENBRIDGE_TYPO_DICTIONARY_DIR`, or use `SUGGEST_ONLY`
for that tenant.

### An expansion target shows `PROVIDER_UNAVAILABLE` or `UNSUPPORTED_LANGUAGE_PAIR`
**Cause:** no installed translation provider covers the pair.
**Fix:** add a glossary to `QWENBRIDGE_MULTILINGUAL_GLOSSARY_DIR`, or enable the model provider
(`QWENBRIDGE_MULTILINGUAL_PROVIDERS`). A target without a translation never carries text.

## API and SDKs

### `/api/v1/...` returns 409
**Cause:** the legacy v1 API is not tenant-aware, so it is refused when authentication is
required.
**Fix:** use `/api/v2`, or run v1 clients against a deployment without authentication.

### 429 `RATE_LIMITED`
**Cause:** a per-IP, per-key, AI-request or token quota was exceeded
(`QWENBRIDGE_ABUSE_*`). The SDKs retry only safe operations.

### A large query returns 400 "Malformed JSON request body"
**Cause:** the query exceeds the size limit. The generic message is deliberate (input hardening).

### The SDK reports an incompatible server
**Cause:** `requireCompatibleServer()` compares `GET /api/v2/version` with the contracts the SDK
supports.
**Fix:** upgrade the SDK or the server; see
[compatibility](../release/QwenBridge-1.0-Compatibility.md).

### The stream ends late after cancellation
**Cause:** server-side cancellation takes effect after the in-flight model call returns. The
stream then ends with `search.failed`, code `CANCELLED`.

## Verification

### The master verification fails at a Docker step
The script uses local Compose services only. It never runs `docker compose down -v`.
**Fix:**
1. Check `docker compose ps`.
2. Check for free ports 6379, 9200 and 11434.
3. Read the step log under `logs/qwenbridge-1.0/`.

### `quarkus_only_audit.py` reports `UNREVIEWED_DOCUMENT`
**Cause:** a new document names the other framework.
**Fix:** reword it, or add the document to the audit's `DOCS` allowlist with its class.

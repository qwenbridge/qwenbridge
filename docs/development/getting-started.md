# Getting Started

This guide takes you from a fresh clone to a database-free server with your own catalog, typo
correction, multilingual expansion and both SDKs. Every command runs from the repository root.

## 1. Prerequisites

| Tool | Version | Needed for |
|---|---|---|
| JDK | 25 | Build and run (`java -version` must print 25) |
| Maven | 3.9+ | Build |
| Ollama | current | The local model (`qwen2.5`, embeddings `bge-m3`); Docker or a native install |
| Node.js | 20+ (22.12+ for the SDK's development tools) | TypeScript SDK only |
| Docker | optional | Compose stack |

`source scripts/verification/java-env.sh && configure_java25` selects a JDK 25 on macOS and Linux
when the default `java` is older.

## 2. Build

```bash
mvn -B clean install -DskipTests      # about 30 s
mvn -B clean verify                   # with all tests, a few minutes
```

## 3. Start the model

```bash
docker compose up -d qwenbridge-ollama qwenbridge-ollama-init
# or natively: ollama serve & ollama pull qwen2.5 && ollama pull bge-m3
```

## 4. Run the server, database-free

```bash
mvn -pl qwenbridge-server quarkus:dev
# or the packaged application (prod profile), without API keys for a local trial:
QWENBRIDGE_SECURITY_AUTHENTICATION_REQUIRED=false \
  java -jar qwenbridge-server/target/quarkus-app/quarkus-run.jar
```

**Authentication:**
- **Packaged application:** runs the `prod` profile, which requires API keys. Without principals,
  every request is refused with 400 "Authentication requires configured principals"; see step 8.
- **Local trial:** with `QWENBRIDGE_SECURITY_AUTHENTICATION_REQUIRED=false` (or in dev mode),
  requests without a token act as tenant `public` with the search role only. The server logs a
  warning.

**Defaults:**
- **Search:** in-process (`qwenbridge.commerce.backend=memory`, `qwenbridge.search.backend=in-memory`).
- **Platform state:** in memory.
- **Redis and OpenSearch:** not needed. The server logs that Redis is unreachable and continues
  with in-memory rate limits and no analysis cache.

To keep platform state (packs, licences, sessions) across restarts:

```bash
QWENBRIDGE_SECURITY_AUTHENTICATION_REQUIRED=false \
QWENBRIDGE_PLATFORM_STORE=file QWENBRIDGE_PLATFORM_FILE_DIR=$HOME/.qwenbridge/state \
  java -jar qwenbridge-server/target/quarkus-app/quarkus-run.jar
```

## 5. Load a catalog

The in-process backend loads JSON catalog files at startup:

```bash
QWENBRIDGE_SECURITY_AUTHENTICATION_REQUIRED=false \
QWENBRIDGE_DEMO_DATASET_FILES=e2e/dual-sdk/fixtures/catalog-e2e-1.0.json \
QWENBRIDGE_DEMO_EMBED=true \
  java -jar qwenbridge-server/target/quarkus-app/quarkus-run.jar
```

- **File format:** `e2e/dual-sdk/fixtures/catalog-e2e-1.0.json` shows it (products with variants,
  prices, currencies, availability, attributes).
- **Embeddings:** `QWENBRIDGE_DEMO_EMBED=true` computes them at load time with the configured
  model, which enables vector and hybrid retrieval.
- **Continuous sync from an HTTP feed:** use `POST /api/v2/catalog/sync`. It needs Redis; see
  [Troubleshooting](../operations/troubleshooting.md#catalog-sync-returns-503).

## 6. First search

```bash
curl -s http://localhost:8080/api/v2/search -H 'Content-Type: application/json' \
  -d '{"query":"black running shoes under 100 EUR","contract":"commerce-search/1.0"}' | jq '.status, .queryIR.constraints, [.results.hits[].id]'
```

Check that each constraint quotes your query (`span`), and that every hit satisfies the hard
constraints.

## 7. Turn on typo correction and expansion

```bash
QWENBRIDGE_SECURITY_AUTHENTICATION_REQUIRED=false \
QWENBRIDGE_TYPO_ENABLED=true \
QWENBRIDGE_TYPO_LANGUAGES=en,sv \
QWENBRIDGE_TYPO_MODE=SAFE_AUTO_CORRECT \
QWENBRIDGE_TYPO_DICTIONARY_DIR=e2e/dual-sdk/fixtures/typo \
QWENBRIDGE_MULTILINGUAL_ENABLED=true \
QWENBRIDGE_MULTILINGUAL_TARGET_LANGUAGES=en,sv \
QWENBRIDGE_MULTILINGUAL_GLOSSARY_DIR=e2e/dual-sdk/fixtures/glossary \
  java -jar qwenbridge-server/target/quarkus-app/quarkus-run.jar
```

`GET /api/v2/spelling` and `GET /api/v2/multilingual` show the effective configuration. Every
search response then carries a `spelling` and an `expansion` section. Per-tenant overrides use
`QWENBRIDGE_TYPO_TENANTS` and `QWENBRIDGE_MULTILINGUAL_TENANTS` (JSON).

More detail: [typo correction](../architecture/typo-correction.md) and
[multilingual expansion](../architecture/multilingual-expansion.md).

## 8. Require API keys

```bash
QWENBRIDGE_SECURITY_AUTHENTICATION_REQUIRED=true \
QWENBRIDGE_SECURITY_API_PRINCIPALS='[{"key":"<at least 24 random characters>","subject":"alice","tenant":"shop-a","roles":["search"]}]' \
  java -jar qwenbridge-server/target/quarkus-app/quarkus-run.jar
```

**Profile behaviour:**
- **`prod`:** authentication is required unless explicitly disabled. Without principals the
  server starts, but refuses every request with 400 "Authentication requires configured
  principals", because the identity configuration is checked on first use.
- **Legacy v1 API:** refused (409) when authentication is required.

Keep keys out of source control.

## 9. Use the SDKs

Short examples are in the [README](../../README.md#6-java-sdk). The complete runnable consumers are
in [`e2e/dual-sdk`](../../e2e/dual-sdk/README.md):
- `java-consumer/` is a standalone Maven project that uses the SDK artifact.
- `typescript-consumer/` installs the SDK from its `npm pack` tarball.

## 10. Optional services

| Service | Start | Select |
|---|---|---|
| OpenSearch | `docker compose --profile opensearch up -d`, then `bash scripts/opensearch-seed.sh` | `QWENBRIDGE_COMMERCE_BACKEND=opensearch QWENBRIDGE_SEARCH_BACKEND=opensearch` |
| Redis | `docker compose up -d qwenbridge-redis` | Used automatically when reachable: analysis cache, rate limits, catalog sync |

## Next steps

- [Configuration](../operations/configuration.md)
- [Testing](testing.md)
- [Architecture overview](../architecture/overview.md)
- [REST API](../api/rest-api.md)

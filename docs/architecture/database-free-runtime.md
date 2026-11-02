# Database-Free Runtime and Connector Isolation (QB-293)

> **Current state after QB-287 (ADR 0058).** Sections 1, 3 and 5 are the QB-293 audit record.
> Since then:
> - **Removed:** the Elasticsearch, PostgreSQL, MongoDB and Shopify connector modules, with
>   their built-in backends.
> - **Built-in backends:** the in-process backend (the default: keyword, vector and hybrid) and
>   the optional OpenSearch backend.
> - **OpenSearch** is contacted only when selected; even the readiness check skips it otherwise.
> - **Redis** never gates startup (Compose `required: false`). Catalog synchronization, the one
>   feature that needs Redis, answers a typed 503 without it.
> - **Extension points:** future adapters plug in through the Core `SearchProvider`, `Connector`
>   and Connector Pack SPIs.
>
> See [architecture overview](overview.md) and the
> [remediation proposals](remediation-proposals.md).

QwenBridge is a domain-neutral, real-time intelligence engine. It needs no database or search
engine:
- **PostgreSQL, MongoDB, Elasticsearch and OpenSearch** are optional connectors only.
- **Shopify** is an optional integration-test target only.
- **Redis** is an optional short-lived cache.

ADR 0053 records the decisions. The audit below was taken on 2 November 2026 over 1,371 tracked
and untracked source files (generated output, logs and benchmark data excluded).

## 1. Dependency inventory

The audit counted references to the four data technologies, Shopify and Spring in each area.
"Classification" uses the seven categories of QB-293.

| Area | PostgreSQL | MongoDB | Elasticsearch | OpenSearch | Shopify | Classification |
|---|---:|---:|---:|---:|---:|---|
| `qwenbridge-core` main | 2 | 1 | 1 | 1 | 0 | **6 Documentation** (Javadoc only). No dependency (guarded) |
| `qwenbridge-core` tests | 12 | 9 | 1 | 4 | 1 | 4 Test-only (fixture strings) |
| `qwenbridge-connector-postgresql` | 41 | | | | | **3 Optional connector** (JDBC driver owned here) |
| `qwenbridge-connector-mongodb` | | 57 | | | | **3 Optional connector** (Mongo driver owned here) |
| `qwenbridge-connector-elastic` | | | 29 | 12 | | **3 Optional connector** (HTTP; Elasticsearch and OpenSearch) |
| Connector module tests | 25 | 32 | 19 | 20 | | 4 Test-only (unit and opt-in live tests) |
| `qwenbridge-server` main | 45 | 49 | 41 | 181 | 0 | **2 Server dependency, optional at run time**: built-in Commerce backends and the v1 OpenSearch provider, inactive until configured. Guarded by tests |
| `qwenbridge-server` configuration | | 2 | | 8 | | 5 Configuration (connector settings; none required) |
| `qwenbridge-server` tests | 91 | 81 | 66 | 371 | 11 | 4 Test-only |
| `qwenbridge-commerce-pack` | | | | | 10 → **0** | Was runtime (Shopify mapping); **moved** to the Shopify module |
| `qwenbridge-connector-shopify` | | | | | 21 | Optional integration-test module (test scope in the server) |
| POM files | 3 | 3 | 3 | 1 | 2 | 3 Optional connector (module dependencies); Shopify is test scope |
| Docker Compose files | 11 | 12 | 11 | 24 | 4 | 5 Configuration. OpenSearch is now an optional profile; the others are in test-only Compose files |
| `.env.example` | 9 | 5 | 6 | 12 | 4 | 5 Configuration (commented optional settings) |
| Scripts | 12 | 8 | 10 | 97 | 1 | 4 Test-only (verification enables the connector profiles explicitly) |
| Documentation | 71 | 67 | 77 | 215 | 95 | 6 Documentation. The README and configuration guide are updated; historical ADRs and reports are kept as records (**7 Legacy references**, not behaviour) |
| TypeScript SDK | 1 | 1 | 1 | 2 | 2 | 6 Documentation and 4 test fixtures (backend names) |

**No mandatory infrastructure anywhere:**
- no Hibernate ORM, Panache, Agroal or JDBC datasource, and no Quarkus Mongo, Elasticsearch or
  OpenSearch client;
- no Spring dependency.

`quarkus-hibernate-validator` is Bean Validation, not ORM.

## 2. Core independence

`qwenbridge-core` has one runtime dependency, `jackson-databind`. It imports no driver,
framework, `java.sql` or HTTP client, and runs without credentials, services or internet access.
Two tests guard this: `DriverDependencyTest` and `ArchitectureNeutralityTest`.

## 3. Connector isolation matrix

| Connector | Module | SPI | Activation | Eager connection at startup | Mandatory configuration | Timeouts | Failure isolation | Cleanup | Tests |
|---|---|---|---|---|---|---|---|---|---|
| OpenSearch | `qwenbridge-connector-elastic` (pack `elastic`); server v1 provider and Commerce backend | Core `Connector`, `SearchProvider` | Connector Pack `elastic` (off by default); `qwenbridge.search.backend=opensearch`; `qwenbridge.commerce.backend=opensearch` | None. HTTP per call | None | Connect 5 s, read 30 s; connector `timeout` 10 s | Runtime per connector, circuit breaker; v1 and v2 errors are typed | `stop()` | `ElasticConnectorTest`, `ElasticLiveTest`, `ConnectorPackOutageTest` |
| Elasticsearch | `qwenbridge-connector-elastic` | same | Pack `elastic`; `qwenbridge.connectors.elasticsearch.url` | None | None | Connector `timeout` 10 s | same | `stop()` | same |
| PostgreSQL | `qwenbridge-connector-postgresql` (pack `postgresql`) | Core `Connector`, `SearchProvider` | Pack `postgresql`; `qwenbridge.connectors.postgresql.url` | None. Pool created at `start()` only | None | `acquire-timeout` 5 s, `query-timeout` 15 s | same; read-only by default | Pool closed in `stop()` | `PostgresConnectorTest`, `PostgresLiveTest` |
| MongoDB | `qwenbridge-connector-mongodb` (pack `mongodb`) | Core `Connector`, `SearchProvider` | Pack `mongodb`; `qwenbridge.connectors.mongodb.uri` | None. Client created at `start()` only | None | Server selection = `acquire-timeout`, connect 5 s, `query-timeout` 15 s | same; read-only by default | Client closed in `stop()` | `MongoConnectorTest`, `MongoLiveTest` |

**Lifecycle and isolation tests:** `ConnectorPacksTest` covers:
- disabled packs create nothing;
- authentication errors, outages, timeouts, open circuits and shutdown stay inside their pack.

**Packaging:** the connector libraries and their drivers ship in the server distribution as
optional libraries, inactive without configuration.

**Future adapters:** none are designed here.

## 4. Docker Compose

**Profiles in `docker-compose.yml`:**

| Command | Services |
|---|---|
| `docker compose up` | `qwenbridge-ollama`, `qwenbridge-ollama-init`, `qwenbridge-redis`. **No database or search engine** |
| `--profile production` | Adds `qwenbridge-app`: in-process retrieval (`QWENBRIDGE_SEARCH_BACKEND=in-memory`, `QWENBRIDGE_COMMERCE_BACKEND=memory`) and file-backed platform state on the `qwenbridge-state` volume |
| `--profile opensearch` | Adds `qwenbridge-opensearch`. The app waits for it only when the profile is enabled (`depends_on … required: false`) |
| `--profile performance` | Adds the k6 load generator |

**Test backends:** PostgreSQL, MongoDB and Elasticsearch are only in
`docker-compose.table1-tests.yml`, and authenticated OpenSearch is only in
`docker-compose.security-tests.yml`. Both are started explicitly by the verification.

**Coupling beyond `depends_on`:** the app's backend selection used to default to OpenSearch
(`qwenbridge.commerce.backend`, and the v1 plan backend taken from the model's analysis). Both are
now configuration with in-process defaults.

**Verification:** the scripts enable `production,opensearch` (`COMPOSE_PROFILE`, comma-separated)
and select the OpenSearch backends explicitly.

**Destructive operations:** none were run. `03-docker.sh` still contains an opt-in
`compose down -v` behind `FORCE_FRESH=true`. It is pre-existing, unused by these runs, and
unchanged.

## 5. Shopify boundary

- **Mapping moved:** the Shopify catalog mapping moved from the Commerce pack to
  `qwenbridge-connector-shopify`. It is registered through `ServiceLoader` only when that module
  is on the classpath.
- **Test scope only:** the server depends on the module in **test scope only**. The packaged
  runtime (`quarkus-app/lib`) contains no Shopify code; the verification checks this.
- **Commerce intelligence kept:** universal commerce intelligence (normalization, the JSON feed
  mapping, the vocabulary) is unchanged.
- **Product identity:** the README and product documentation no longer present Shopify as part of
  the product.

## 6. Redis and vector processing

**Approved uses of Redis**, all optional:

| Use | TTL | When Redis fails |
|---|---|---|
| Analysis cache | `qwenbridge.analysis.cache.ttl` | Falls back to computing the analysis (`cache_fallbacks` metric) |
| Rate limiting | Fixed windows | Falls back to the in-memory limiter |
| Commerce evidence and search analytics | 1 h and 10 min | **New:** fall back to a bounded in-process store with the same TTLs. Data written during an outage is local to the instance. `RedisOutageFallbackTest` covers it |

**Cache isolation:** keys are tenant- and prefix-scoped.

**No authoritative state in Redis:**
- Platform state (licenses, packs, sessions, tenant schemas) defaults to **memory**, with a
  startup warning.
- The Docker stack uses the **file** store.
- Redis is used for platform state only when chosen explicitly (`qwenbridge.platform.store=redis`)
  for several instances sharing state.

**Vector processing needs no vector database.** `InMemoryVectorIndex` keeps vectors in process,
per embedding space. Its limits:
- bounded by `maxDocuments` and the heap;
- rebuilt on restart (no persistence);
- exact cosine search (no approximate index), so it is linear in index size: about 3.6 ms p95 at
  10,000 × 384 dimensions, top-20, concurrency 8 (Core benchmark);
- single-node;
- needs an embedding provider; without one, vector retrieval reports unavailable.

**v1 in-memory provider:** the v1 pipeline's in-memory provider is a keyword-only demo. Constrained
or vector v1 queries without OpenSearch are refused with a typed 400. The full database-free path
is v2 search on the in-process Commerce backend (filters, keyword, vector and hybrid).

## 7. Runtime evidence

| Check | Evidence |
|---|---|
| Real packaged application, none of the four technologies (and no Redis) | Master step "QB-293: database-free runtime" (`scripts/verification/21-database-free-runtime.sh`) |
| Health | `/q/health/ready` 200; `/api/v1/health/ready` reports Redis as DEGRADED and does not probe the unselected OpenSearch (QB-287) |
| Language processing | Persian query detected and translated |
| QueryIR | Grounded constraints: brand, category, color, currency, price, size |
| v2 REST | In-process results |
| Security and policy | Injection blocked; anonymous callers refused for packs and agents |
| v1 | Typed refusal (see §6) |
| SSE | Java SDK v2 stream: 2 events |
| SDKs | Java SDK v2 client, TypeScript SDK v2 client |
| Tests | `CleanStartTest`, `ServiceFreeE2ETest`, `RedisOutageFallbackTest`, `ConnectorPackOutageTest` |

## 8. Regression guards

`ArchitectureNeutralityTest`, with `DriverDependencyTest`, `ShopifyIsolationTest` and
`CommerceSeparationTest`, fails the build when any of these appears:
- a connector or driver dependency (or framework import) in Core;
- a mandatory datasource, ORM or Mongo, Elasticsearch or OpenSearch client;
- a default Compose service for one of the four technologies, or a hard dependency on one;
- Shopify in a runtime module, in the server's runtime scope, or in a service registration;
- Spring Boot.

Each guard was checked by injecting the violation and seeing the test fail.

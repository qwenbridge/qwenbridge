# Commerce API v2 — Quarkus and Java 25

The tenant-aware commerce path is `POST /api/v2/search`. It translates and analyzes
the source sentence, checks deterministic and model security decisions, validates
a typed plan, executes OpenSearch and checks every returned product against that
plan. A failed model, unknown field/operator, ambiguous requirement or contradiction
never silently becomes an unrestricted successful search.

```json
{"query":"کفش مشکی نایکی سایز ۴۲ زیر ۱۰۰ یورو، غیر استوک و موجود","catalogLanguage":"en","schemaId":"commerce","schemaVersion":1,"mode":"HYBRID","rerank":false}
```

`mode` accepts KEYWORD, VECTOR or HYBRID. The default is KEYWORD. Translation defaults
to English; `catalogLanguage` requests a catalog language through the configured AI
provider. The response carries `apiVersion:2`, request identity, translation,
intent, `queryIR`, products, clarification questions, evidence, suggestions and a
confidence assessment. Scores are **uncalibrated model signals**, not probabilities.
Statuses include OK, CLARIFICATION_REQUIRED, REVIEW and BLOCKED. Transport/auth/backend
errors use the common HTTP error contract. Clients must check both HTTP and status.

`POST /api/v2/search/stream` emits versioned `search.started` and a terminal
`search.completed` or `search.failed` event. Java and TypeScript commerce clients
share the REST/refinement/SSE contract. V2 streams carry final typed results;
the existing V1 stream additionally carries intermediate analysis/token events.

## Identity and access

Configure `qwenbridge.security.authentication-required=true` and the secret JSON
array `qwenbridge.security.api-principals` in a deployed tenant environment.
Bearer credentials map to a subject, tenant and explicit roles. Caller-supplied
tenant headers and constraint plans cannot override that mapping. Invalid provided
credentials are rejected even in anonymous mode. Anonymous local development is
limited to the public tenant and search role. Authenticated mode requires V2 for
tenant operations; V1 search is refused to prevent a tenant bypass.

`search` permits search, owned refinement and owned query evidence. Administrative
roles are `schema:write`, `catalog:write`, `analytics:read`, `analytics:write`,
`evidence:read`, `evidence:write`, `plugins:read`. Credentials are static service/API
principals; this implementation does not claim an OIDC login/session system.

## Schemas, types and execution

`GET /api/v2/schemas/{id}/{version}` reads a version; `POST /api/v2/schemas`
registers an immutable tenant-owned version. Custom definitions persist in Redis;
register an incremented version to change fields, operators or backend mappings.
The built-in commerce/1 contract is immutable. Registry persistence follows the
operator's Redis persistence/backup policy; it does not migrate existing index data.

Supported built-in fields are brand, color, size, condition, category, price,
currency, inStock, weightKg, availableAt and location. A field defines type,
allowed operators, normalization and its OpenSearch field mapping. Default term
fields use lowercase keyword normalizers; currency is an ISO uppercase code.
Numerical bounds use decimal arithmetic, dates use ISO local dates and booleans
are strict. Prices need an explicit currency; automatic exchange conversion is
not performed. Weight is canonical kilograms. Contradictory ranges/equalities
request clarification. Ambiguous grouped price separators are rejected.

Filters apply before ranking in keyword, Lucene kNN and hybrid retrieval and
include the verified tenant. NE requires a known field value, so missing product
attributes cannot qualify as evidence that a negative condition holds. The built-in
backends are the in-process backend (default) and optional OpenSearch; other engines are
future adapters through the Connector Pack SPI (QB-287). Index mappings must match the selected schema. Seed creates the
default mapping in a fresh disposable verification index.

## Refinement, attribution and evaluation

`POST /api/v2/search/{requestId}/refine` accepts a new query and preserves all
previous verified fields except fields explicitly changed by the new query. Explicit
relaxations such as "any size", "any brand" and "no price limit" clear that field;
remaining previous fields are retained. Other ambiguous changes request clarification.
The previous search must belong to the same subject and tenant, have status OK,
and use the same schema version. `GET /api/v2/search/{requestId}/suggestions`
returns ordered model rewrites with the base identity; apply one through refine
to preserve the previous constraints. These are model suggestions, not a
popularity/autocomplete service. Stored searches expire after ten minutes and
are capped at 2,048 per tenant; this is bounded refinement, not durable sessions.

Evidence includes identifiers, source URNs, occurrence/observation times, exact
source spans and a parent chain. Some model-extracted constraints cite the whole
original sentence: these are broad source spans, not precise field-level semantic
proofs. Query source text is retrievable via `GET /api/v2/evidence/{id}/source`
with ownership checks. `POST /api/v2/evidence` records external source text and
verified spans with evidence:write; external retrieval requires evidence:read.
Evidence storage retention is one hour; access to owned query evidence also
depends on the ten-minute stored-search ownership record. Backend execution attribution records the index
and returned IDs; index contents can subsequently change.
Analysis cache keys preserve the exact analysis text (including casing and
whitespace) so cached source spans cannot be reused for a different original
sentence. Cache generation v10 separates this contract from earlier entries.

Search/click interactions are tenant-scoped. Clicks must reference an actual
returned product and the same subject. GET /analytics requires analytics:read;
POST /analytics/click requires search. POST /analytics/evaluate and
/analytics/calibration require analytics:write and compute retrieval metrics
(Precision@K, Recall@K, MRR, nDCG) and Brier/ECE on supplied reference labels.
Calibration tools do not make model scores calibrated. The live multilingual
reference corpus is small and controlled; passing it does not establish universal
language coverage or production relevance improvement.

## Catalog sources

`POST /api/v2/catalog/sync` requires catalog:write and a configured trusted endpoint.
The HTTP JSON adapter requests `GET endpoint?cursor=<opaque>` with an optional
Bearer credential and expects:

```json
{"changes":[{"id":"source-1","deleted":false,"document":{"title":"Black shoes","price":99,"currency":"EUR","brand":"Nike","color":"black","size":"42","inStock":true,"condition":"new","category":"shoes"}}],"nextCursor":"page-2","hasMore":true}
```

Deleted entries use `deleted:true`. Source tenant IDs are replaced by the verified
tenant; IDs are namespaced by tenant/source. Ingestion generates embeddings from
title/description through the AI provider. Redis leases serialize source sync;
checkpoints advance only after a page applies. Writes/deletes are idempotent and
429/5xx/transport failures retry up to three times. The adapter stops after 100
pages with PARTIAL and a resumable checkpoint. Configure the index mapping before
sync. Currency and stock must come from the source; missing currency is not guessed.

Catalog synchronization needs Redis for its checkpoint and lock; without Redis a
sync request is refused with 503 (QB-287). The built-in source kind is `http-json`;
other sources (for example a commerce platform) are separately installed
`CatalogConnectorFactory` implementations. QwenBridge ships no Shopify connector
(removed in QB-287); Shopify is only an optional end-to-end test target
(`e2e/dual-sdk`).

## Providers and extensions

The separate `qwenbridge-plugin-sdk` artifact has no Quarkus dependency. Implement
`QwenBridgePlugin`, provide a manifest (semantic version, API version 1, capabilities),
and register the implementation in
`META-INF/services/io.qwenbridge.plugin.QwenBridgePlugin`. Place the compiled JAR
in `qwenbridge.plugins.directory` before startup. Incompatible/duplicate IDs fail
startup. Only administrator-trusted code belongs there; there is no code sandbox
or hot install/marketplace flow. `PluginExternalJarTest` compiles a plugin using
only the public SDK and loads its actual JAR outside the core compilation.

The optional reranker plugin declares rerank and returns `ids` as a complete
permutation. The built-in model reranker uses constrained candidate IDs, bounded
workers/timeouts and falls back to the exact baseline on errors. A controlled
reference measures nDCG uplift; representative merchant relevance still needs
merchant judgments.

Ollama and the second `http-json` provider share chat, streaming, embedding and
structured output capability contracts. The HTTP JSON gateway protocol is:

- POST /chat: `{prompt,outputSchema?}` → `{content:string}`.
- POST /embeddings: `{text}` → `{vector:[finite numbers]}`.
- POST /stream: `{prompt,outputSchema?}` → NDJSON `{content:string,done:boolean}`;
  a terminal done=true record is required.

Configure its base URL/token and select qwenbridge.ai.provider=http-json. It is
a gateway adapter with an independent local protocol acceptance test, not a claim
that an external vendor has been provisioned. GET /api/v2/ai/providers reports
capabilities/configuration. OpenSearch service authentication uses Basic or Bearer
configuration and a trusted TLS CA; inbound user credentials are never forwarded.
The complete verifier also tests a separate secured TLS OpenSearch instance.

## Verification

Use `bash scripts/qwenbridge-final-verification.sh`. It builds Java 25/Quarkus,
seeds an adversarial catalog, runs unit/live integration/SDK tests, formatting and
static analysis, TLS/auth checks, REST/SSE checks and seven k6 workload entrypoints.
Commerce quality checks every explicit filter and the actual returned product in
eleven languages plus mixed language; throughput repeats a warm reference query.
The million-request phase repeats the documented small warm corpus with 250 VUs;
it does not mean one million concurrent people or unique cold AI requests.

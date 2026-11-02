# Table 1 — Commerce platform contracts

> **Historical record.** It describes the repository at the time it was written. Modules and connectors it mentions may have been removed since: QB-287 removed the Elasticsearch, PostgreSQL, MongoDB and Shopify connector modules and `examples/` (ADR 0058). For the current state, start at [docs/README.md](../README.md).

The server uses Quarkus/CDI and Java 25. `qwenbridge-plugin-sdk`,
`qwenbridge-commerce-pack`, and `qwenbridge-library-pack` are independent Maven
artifacts. The pack modules depend only on the public SDK and the JDK.

## Catalog and domains

`POST /api/v2/catalog/normalize` requires `catalog:write`. It returns canonical
product/variants, ISO money, inventory (including **unknown**), kilograms, source
and relationships. Invalid currency precision and contradictory inventory fail
validation. Ingestion expands variants and reconciles removed variants using
source manifests; it does not invent available stock.

`POST /api/v2/catalog/canonical?connector=http-json|shopify` requires
`catalog:write` and stores nothing. It returns:

- `product`: the canonical product (schema 1.0), with provenance on every value
  and certainty on attributes;
- `priceRanges`: one range per currency, never converted;
- `productText`: the versioned embedding text and its fingerprint.

Field meaning comes from the Commerce pack's catalog mappings. See ADR 0029.

`POST /api/v2/domains/{domain}/normalize` and `/documents` require `catalog:write`
and accept `{id,data,backend?}`. `/normalize` previews all searchable documents;
`/documents` writes them to the selected backend. `/search` requires `search` and
accepts `{query,backend?,connectorPack?,mode?,limit?,cursor?}`. It executes the
installed domain's understanding/schema, common validation, policies and backend.
An external Connector Pack can execute through the same engine; every returned
document must satisfy domain, tenant and typed constraints.

Commerce and Library use this same path. Library supports the declared English
grammar `books by Ada after 2020`. The bundled Commerce rule grammar recognizes
listed brands, basic colors, categories, size, explicit ISO budgets, stock and
refurbishment. Unrecognized text asks for clarification. The main
`POST /api/v2/search` remains the richer model-backed multilingual search path;
its schema contract comes from the Commerce module.

## Conversation and explicit alternatives

- `POST /api/v2/sessions`: `{ttlSeconds?:1800,backend?,lockedFields?:[]}`.
  TTL is 60–3600 seconds, absolute from creation, persisted in Redis in production.
  Enable authentication to distinguish shopper identities; the optional anonymous
  development mode shares the public anonymous identity.
- `GET /api/v2/sessions/{id}`: only the creating subject in its verified tenant.
- `POST /api/v2/sessions/{id}/turns`: `{query,expectedRevision?,resetContext?:false,mode?:"KEYWORD",rerank?:false}`.
- `/turns/stream` accepts the same body and emits finite `session.started`,
  `session.completed` (with the saved session), or `session.failed` events.
  Closing an SSE connection does not delete the conversation. Reload a session
  after a transport failure before retrying. A revision conflict returns 409.
- A clarification answer is appended to the unresolved original input and
  analyzed again; previous conditions remain in that source. A normal refinement
  replaces explicit equalities or bounds in the same direction. Adding a lower
  price limit preserves the upper budget and prior negative conditions.
- `GET /api/v2/sessions/{id}/relaxations` returns specific expiring proposals only
  for a verified zero-result search. No broadened search runs at this step.
  Proposals can raise an upper budget by 10% or remove one color/size requirement.
  Currency, brand, category, stock, negations and `lockedFields` are preserved.
- `POST /api/v2/sessions/{id}/relaxations/{proposal}/accept` takes
  `{expectedRevision}`. Only the owner can accept the server-held plan. The result
  becomes the next turn's context; a stale proposal cannot replace newer context.
  Repeating a completed acceptance is idempotent.
- Stateless alternatives also exist at `GET /api/v2/search/{requestId}/relaxations`
  and `POST /api/v2/relaxations/{id}/accept`. Requests must remain owned and retained.

Search evidence is kept for one hour; search analytics/refinement lookup is ten
minutes. Sessions hold their verified response independently of that analytics
lookup. Retained evidence spans reference the original source record, rather than
being reassigned to the latest message.

## Backend capabilities

| Backend | Modes | Mapping and paging |
|---|---|---|
| OpenSearch | Keyword, Vector, Hybrid | Existing native kNN mapping; tenant/typed filters |
| Elasticsearch | Keyword, Vector, Hybrid | Independent dense_vector/kNN HTTP implementation; Basic or Bearer auth |
| PostgreSQL | Keyword | JSONB, typed prepared parameters and bound JSON paths; full-text tokens |
| MongoDB | Keyword | BSON types, Decimal128, escaped literal matching; tenant compound index |
| Memory (`memory`) | Keyword | In process, no external service: Core BM25 index, the same plan predicate as the result check; per instance, lost on restart; base `search` entitlement |

The backend name in a request is checked against the configured registry, not a
list in the contract. An unknown name returns 400. All five connectors also
implement the Core unified search SPI (ADR 0028, ADR 0030).

### Commerce Search Contract 1.0

A search request with `"contract": "commerce-search/1.0"` (on `/api/v2/search`,
`/stream` and refine) works as follows:

- **Interpretation.** The query is interpreted over the universal QueryIR, and
  values are canonicalized with the catalog vocabulary.
- **Strategy.** The Core planner chooses keyword, vector or hybrid retrieval
  from the query's needs and the backend's providers. `mode` must be omitted or
  `AUTO`.
- **Exclusions.** These use the neutral semantics: a product that lacks the
  field is kept.
- **Subjective words** (comfortable, lightweight, …) rank but never filter.
- **Multi-product queries** are answered per subquery in `subqueries[]`.
- **Paging.** Pages reach 30 results deep.

The response adds:

- `contract`;
- `retrieval`: the requested and executed strategies, planner reasons,
  degradations, `degraded`, results removed by validation, providers and
  warnings;
- `interpretation`: intents, retrieval needs, semantic signals and a validation
  status per constraint.

Results are ranked by the versioned policy (`qwenbridge.ranking.policy.*`,
optional `qwenbridge.ranking.business-rules`). With `rerank: true`, the top
window is reordered by the model reranker, guarded so that a slow or invalid
answer keeps the baseline. Ranking never adds or removes results. Each hit's
metadata carries its retrieval score and score components (ADR 0032).

Statuses add `UNSUPPORTED` and `PARTIAL`. Requests without `contract` keep the
apiVersion 2 behaviour, and their responses are unchanged. See ADR 0031.

### Zero-result recovery

`GET /api/v2/search/{id}/recovery` explains the recovery decision for a search.
It returns:

- the outcome: `RESULTS`, `ZERO_RESULTS` or `PROVIDER_FAILED` (a failed provider
  is never answered by broadening);
- the relaxation policy;
- the proposals;
- every constraint that was kept, with its reason.

Proposals change one condition each, need explicit acceptance, and never touch
exclusions, protected fields (tenant, currency) or locked fields. An accepted
offer is re-validated before it runs.

The policy is configured with `qwenbridge.commerce.relaxation.*`. See ADR 0033.

### Suggestions, refinement and feedback

- **`GET /api/v2/search/{id}/suggestions`** adds typed `items`
  (`version: suggestions/1.0`). These are grounded refinements from the
  returned results, with support and evidence, in a deterministic order.
  `queries` keeps the previous strings.
- **`POST /api/v2/search/{id}/suggestions/{sid}/apply`** applies one refinement
  and keeps every other condition.
- **`POST /api/v2/search/{id}/feedback`** takes
  `{kind: NOT_RELEVANT|EXCLUDE_VALUE|TOO_EXPENSIVE, documentId?, field?, value?}`.
  It returns the typed update and the follow-up search.

Under `commerce-search/1.0`, clarifications are typed and targeted
(`clarifications`). See ADR 0034.

### Conversation context

- **`GET /api/v2/sessions/{id}/context`** returns the session's context
  (`conversation-context/1.0`): constraints with origin and turn, locked fields
  and turns. A token is included when signing is configured.
- **`POST /api/v2/conversations/turns`**
  (`{query, context | contextToken, backend?, limit?}`) runs a stateless
  follow-up and returns the response, the next context and a token.

Context is re-validated on every use:

- It must belong to the caller (403 otherwise).
- It must not be expired (410) and must stay within its limits (413).
- Its fields must be in the schema.
- Unsigned context is downgraded to CONTEXT provenance.

See ADR 0035.

`GET /api/v2/backends` reports configured capabilities. Search requests add
`backend`, `limit` (1–100) and `cursor`; responses add `nextCursor`, actual stage
`timings`, `policies`, and `lockedFields`. Cursors are bound to subject, tenant,
backend, query, mode, schema and constraints. The page window is bounded at 10,000.
Unsupported modes return 422 before connection or embedding. Every result is
checked against the executed tenant/constraint plan.

Elasticsearch uses normal JVM certificate verification (custom trust via the JVM
truststore), rejects embedded URL credentials and redirects, and has a configurable
positive timeout up to 60 seconds (default 30). PostgreSQL uses connection/socket/
statement deadlines and bounded search concurrency; configure `sslmode=verify-full`
for TLS. MongoDB uses driver URI TLS settings, bounded pools and timeouts. No
connector bypasses certificate checks. Backend credentials stay in operator
configuration, not public requests. Integration tests run local authenticated
services; Elasticsearch's isolated test fixture uses HTTP on loopback. Existing
OpenSearch acceptance additionally exercises HTTPS with security enabled.

Live versions observed during this work: Elasticsearch 8.19.23, PostgreSQL 16.15,
MongoDB 8.0.34. Other versions require their own compatibility acceptance.
See the [Elasticsearch kNN reference](https://www.elastic.co/guide/en/elasticsearch/reference/8.19/knn-search.html),
[pgJDBC query documentation](https://jdbc.postgresql.org/documentation/query/) and
[MongoDB Java driver documentation](https://www.mongodb.com/docs/drivers/java/sync/current/).

## Packs, licensing and marketplace

`/api/v2/platform/packs` supports GET (`packs:read`) and POST signed installation
(`packs:write`). `/{id}/activate` takes `{version,configuration}`, and `/disable`
and `/rollback` change lifecycle state. Versions are immutable; dependencies,
API compatibility, cycles, kind and executable/signed manifest equality are checked.
A candidate starts before replacing an active version. Configuration is retained
per version for rollback. Variant document identities include their source product,
and canonical source metadata remains structured on all three optional backends. A disabled/replaced pack closes after its last in-flight
lease; its classloader and temporary JAR are released. Pack secrets are explicit
allowlisted `env:NAME` references, never raw values in registry configuration.

The signed envelope is `{keyId,manifest,jar,signature}`: manifest is exact UTF-8
JSON, jar is Base64, and the Ed25519 signature covers UTF-8
`qwenbridge-pack-v1\n<manifest>\n<SHA-256 lowercase hex of raw jar>`.
Registry, license and marketplace state use Redis; configure its durable storage
and backups for the required retention. Application restarts do not remove that
shared state. The local acceptance verifies Redis CAS/TTL across independent
clients; it does not claim disaster-recovery acceptance.

Trust configuration contains only X.509 public keys. A signed Java pack is trusted
server code; signatures authenticate publishers and do not create a code sandbox.

Policy Packs select domains/tenants, execute by order then ID, and record rule,
version, effect and reason. DENY overrides ALLOW, REVIEW stops execution, conflicts
are recorded, and an unavailable active policy denies execution. Policy execution
also checks current entitlements after a downgrade.

`GET /api/v2/platform/editions` exposes the enforced feature matrix:
Community includes core search, sessions, alternatives and bundled packs;
Professional adds external packs and the three optional backends; Enterprise adds
tenant policies and local marketplace publication. Existing core search modes
remain in Community. Every licensed action is checked server-side.

`POST /api/v2/platform/license` requires `license:write` and an Ed25519 envelope
`{keyId,payload,signature}`. Payload is Base64 UTF-8 JSON with
`version:1,id,issuer,tenant,edition,notBefore,expiresAt,features`. The signature
covers the decoded payload bytes. The issuer must match keyId; tenant, time and
feature subset are validated. An absent/expired license falls back to Community;
DELETE explicitly downgrades. Trust keys are operator-managed and not bundled in
production. Tests generate temporary keys; no test issuer is trusted by production.

The local marketplace lists and downloads signed artifacts at
`/api/v2/platform/marketplace[/{id}/{version}]`. Publication requires Enterprise,
`marketplace:publish` and `publisher:<publisher-id>`. It validates the executable,
signature, immutable version and publisher ownership before saving. Download
rechecks the signature; installation is an explicit separate action. This is a
local foundation, with no external public marketplace deployment.

## Web Component and Playground

`/web/web-component.js` registers `<qwenbridge-search>`. Configure it from trusted
site code using `.configure({baseUrl,bearerToken?,transport:"rest"|"sse",backend?,lockedFields?})`.
The credential stays in memory. `.sessionId` and `.resume(id)` support reconnecting
with the same verified owner. The shopper UI uses one text input, results,
clarification/error messages and explicit acceptance buttons. RTL, Enter,
Shift+Enter, text-only product rendering and cancellation are browser-tested.

The npm subpath `@qwenbridge/sdk/web-component` exports the same component and
ships its CSS. Main SDK imports remain safe in Node without an HTMLElement global.

`/playground/` shows original input, translation, IR, decision/policy, confidence,
evidence, real timings and products. Executed filter inspection uses
`GET /api/v2/search/{requestId}/debug`, requires `debug:read`, and checks ownership.
It exposes filter clauses, without credentials or embeddings.

The shopper component calls QwenBridge; Shopify catalog synchronization runs
server-side. Store admin credentials are not passed to the shopper browser.
Shopify protocol tests are deterministic local tests. Actual merchant acceptance
requires a configured store/token and remains separate; no external storefront
or public publication is claimed by local test success.

## Verification

`bash scripts/qwenbridge-final-verification.sh` builds and seeds a fresh index,
runs all opted-in live Java tests, SDK/browser checks and code quality, then
validates runtime contracts and eight k6 scripts. Backend fixtures start with
generated credentials and stop before load testing; normal quotas are restored
afterward. A session workload checks REST, SSE, retained constraints, explicit
consent and persisted results under two virtual users. The million-request
workload measures cached core-search throughput with 250 VUs; it does not
simulate a million simultaneous shoppers or a million unique model calls.

The canonical language corpus is `qwenbridge-server/src/test/resources/commerce/`
`language-cases.json`; regenerate its k6 mirror with
`python3 scripts/verification/generate-language-corpus.py --write`.
Use `PERF_TOTAL_REQUESTS=1000 PERF_VUS=10` for a shorter load run.

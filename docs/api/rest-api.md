# REST API

QwenBridge has two API versions. Both are frontend-neutral JSON over HTTP, and any HTTP client can
use them; the Java and TypeScript SDKs are conveniences, not requirements.

| Version | Base path | Status | Use it for |
|---|---|---|---|
| v1 | `/api/v1` | Stable, maintained | Single-tenant pipeline analysis, AI chat, health, version |
| v2 | `/api/v2` | Stable (1.0) | Tenant-scoped search, sessions, domains, platform, connectors |

`GET /api/v2/version` reports the API versions, the versioned contracts and the limits a server
applies:

| Contract | Version | Changes |
|---|---|---|
| `error` | `api-error/2` | Error body (below) |
| `sse` | `progress-event/2` | v2 progress streams ([sse.md](sse.md)) |
| `commerceSearch` | `commerce-search/1.0` | ADR 0031 |
| `queryIR` | `1.0` | ADR 0015 |
| `connectorConfig` | `connector-config/1` | ADR 0037 |

The complete list of public endpoints is
`qwenbridge-server/src/test/resources/contract/api-endpoints.txt`. A test fails when the server and
that list differ, so no endpoint is added or removed unnoticed.

## Compatibility rules

- **Additive changes** (a new optional field, endpoint, error code or category) may happen in any
  release. Clients must ignore unknown fields and treat unknown error codes by their category.
- **Breaking changes** (removing or renaming a field or endpoint, changing a type or meaning) need a
  new API version or contract version, and migration guidance in this document.
- **v1 error bodies** keep exactly their seven fields, because released v1 SDKs parse them strictly.

## Headers

| Header | Direction | Meaning |
|---|---|---|
| `Authorization: Bearer <key>` | request | Required for `/api/v2` in authenticated deployments (`%prod`) |
| `X-Request-ID` | both | Client correlation ID (`[A-Za-z0-9][A-Za-z0-9._:-]{0,127}`); invalid values are replaced, never echoed |
| `X-Correlation-ID` | both | Groups related requests; defaults to the request ID |
| `traceparent` | both | W3C Trace Context. A valid parent is continued; otherwise a new trace starts |
| `X-Trace-Id` | response | The trace ID of the request |
| `X-Request-Timeout-Ms` | request | v2: the client's time budget in milliseconds (positive integer), capped by `qwenbridge.api.request-timeout` |
| `X-QwenBridge-Version` | response | Server version |

## Errors

Every error is an `ApiError`:

```json
{
  "timestamp": "2026-11-01T12:00:00Z",
  "status": 504,
  "error": "Gateway Timeout",
  "code": "TIMEOUT",
  "message": "Request deadline exceeded",
  "path": "/api/v2/search",
  "requestId": "r-42",
  "category": "TIMEOUT",
  "retryable": true
}
```

`category` and `retryable` are present on `/api/v2` only. Messages are safe for display: secrets
are masked, and stack traces, class names and upstream details are never included. Branch on
`code` or `category`, never on `message`.

| Code | Category | HTTP | Retryable |
|---|---|---|---|
| `VALIDATION_ERROR`, `BAD_REQUEST` | `VALIDATION` | 400, 413, 415, 422 | no |
| `UNAUTHORIZED` | `AUTHENTICATION` | 401 | no |
| `FORBIDDEN`, `TENANT_API_REQUIRED` | `AUTHORIZATION` | 403, 409 | no |
| `NOT_FOUND` | `NOT_FOUND` | 404 | no |
| `CONFLICT` | `CONFLICT` | 409 | no |
| `RATE_LIMITED` | `RATE_LIMIT` | 429 | yes |
| `UNSUPPORTED_CAPABILITY` | `UNSUPPORTED` | 501 | no |
| `TIMEOUT` | `TIMEOUT` | 504 | yes |
| `CANCELLED` | `CANCELLED` | 409 | no |
| `AI_PROVIDER_ERROR`, `SEARCH_PROVIDER_ERROR`, `DEPENDENCY_UNAVAILABLE` | `DEPENDENCY` | 502, 503 | yes |
| `INTERNAL_ERROR` | `INTERNAL` | 500 | no |

v1 keeps its historical codes: v1 dependency and timeout failures that are not provider errors
remain `INTERNAL_ERROR`.

## Deadlines and cancellation (v2)

Search, refine, session turns, stateless conversation turns and domain search run with a deadline.
That deadline is `qwenbridge.api.request-timeout` (default 60 s), or the client's
`X-Request-Timeout-Ms` when that is shorter.

- **Propagation.** The deadline reaches retrieval stages, backend guards and the optional
  OpenSearch calls; each bounds its own timeout by the time left.
- **When the deadline passes,** blocking work is interrupted and the request fails with
  `504 TIMEOUT`.
- **Cancelling.** `DELETE /api/v2/requests/{requestId}` cancels the caller's own in-flight request,
  which then fails with `409 CANCELLED`.
  - Pass your own `requestId` in the search body (or `X-Request-ID` for turns and domain search) to
    be able to cancel.
  - Requests of other principals or tenants, and finished ones, are `404`.
  - A request ID may be in flight only once per tenant; a second concurrent use is `409 CONFLICT`.
- **Not counted as failures.** Cancelled and expired calls never count towards a connector's
  circuit breaker.

## Pagination and limits

- **Page size.** `limit` must be within 1..`qwenbridge.api.max-page-size` (default and hard
  maximum 100). Larger values are refused, never truncated.
- **Commerce search.** Paging uses an opaque, tenant-scoped `cursor` from the previous page.
  - Pages are offset windows over the backend order (score, then document ID). A catalog change
    between pages can shift results; there is no snapshot isolation.
  - The deep-paging window is bounded per backend (10,000 results). Planned `commerce-search/1.0`
    requests use a 30-result candidate window.
- **Domain search** uses the same `limit`/`cursor` rules.
- **v1 analyze** returns one bounded result set and has no paging.

## Extensions and agents (v2)

Plugin SDK 1.0 extensions (ADR 0046). Every endpoint authorizes the caller with the central policy;
a run is visible only to the subject that started it (same tenant and subject), and anyone else
gets `404`.

| Endpoint | Permission | Result |
|---|---|---|
| `GET /api/v2/extensions` | `plugins:read` | Installed plugins: `id`, `version`, `publisher`, `state` (`DISCOVERED`, `REJECTED`, `STARTED`, `STOPPED`, `FAILED`), `reason`, `tools`, `agents` |
| `GET /api/v2/agents` | `agents:read` | `sdkVersion`, the registered agent and tool definitions |
| `POST /api/v2/agents/{agentId}/runs` | `agents:run` | `202` with the run (`status` `RUNNING`); body `{"input": {...}}`; unknown agent `404` |
| `GET /api/v2/agents/runs/{runId}` | owner only | The run: `runId`, `agentId`, `status`, `output`, `steps`, `error` |
| `DELETE /api/v2/agents/runs/{runId}` | owner only | Cancels the run; returns it with `status` `CANCELLED` |

Run `status` is `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED` or `TIMED_OUT`. Each step records
`index`, `kind` (`TOOL`, `DELEGATE`, `NOTE`), `name`, `status` and `reasons`, for example
`DENIED` with `NO_SCOPE`, `APPROVAL_REQUIRED`, `NOT_ALLOWED_FOR_AGENT` or `MAX_STEPS`. Tool calls
are authorized as the caller of the run: an agent never has permissions of its own, and its input
cannot change the tenant or carry an approval.

## Packs (v2)

Read-only inventories, permission `packs:read` (see
[docs/architecture/packs.md](../architecture/packs.md)).

| Endpoint | Result |
|---|---|
| `GET /api/v2/domain-packs` | Active Domain Packs: `packId`, `packVersion`, `domain`, `schemas`, `extension` (`namespace`, `version`, `readableVersions`), `rules` |
| `GET /api/v2/connector-packs` | Every Connector Pack: `id`, `version`, `publisher`, `systems`, `spi`, `state` (`DISABLED`, `IDLE`, `READY`, `DEGRADED`, `FAILED`, `REJECTED`), `reason`, and per connector `state`, `reason`, `declared` and `effective` capabilities, `circuit`, and configuration key names (`settings`, `secrets`); never values |

## AI models (v2)

`GET /api/v2/ai/models` (permission `search`) describes the configured AI providers
([docs/architecture/ai-providers.md](../architecture/ai-providers.md)):
- **`chat`:** `primary`, `fallbacks`, `fallbackPolicy` (`NONE` or `ON_PROVIDER_FAULT`), and per
  provider its `model` (`provider`, `model`, `version`, `configuration`) and `capabilities`
  (`streaming`, `structuredOutput`, `cancellation`, `maxInputChars`, `maxOutputTokens`).
- **`embedding`:** `enabled`, `provider`, `space` (`model`, `version`, `dimensions`), `maxBatch`,
  `maxInputChars`, and `reindexRequiredWhen`.
- **`reranking`:** per reranker its `id`, `maxCandidates` and `model`.

Credentials and endpoints are never included. `POST /api/v1/ai/chat` responses carry
`X-QwenBridge-AI-Provider` (the provider that answered) and, when a fallback answered,
`X-QwenBridge-AI-Fallback: true`.

## Multilingual query expansion (QB-286)

- **`GET /api/v2/multilingual`** (permission `search`): the caller's tenant's effective expansion
  settings:
  - `enabled`, `policy`, `scope`, `minConfidence`, `aliases`;
  - `targets` (`configured`, `language`, `resolution`, `note`, `providers`);
  - `providers` (`id`, `status`, `detail`, `sources`, `targets`, `pairs`, `detectsSource`,
    `requirements`, `metadata`);
  - `limits`.
- **`expansion` on responses.** When expansion is enabled for the caller, v1 analyze responses, v2
  search responses and the v2 SSE terminal event carry `expansion`, with one result per configured
  target language. It is absent otherwise, and absent when the query was stopped by a safety check.
  See [docs/architecture/multilingual-expansion.md](../architecture/multilingual-expansion.md).

## Typo correction (QB-291)

- **`GET /api/v2/spelling`** (permission `search`): the caller's tenant's effective typo settings:
  - `enabled`, `mode`, `scope` (`application` or `tenant`);
  - `languages` (`configured`, `status`, `providers`);
  - `providers` (`id`, `languages`, `scripts`, `capabilities`, `requirements`, `entries`,
    `approximateBytes`).
- **`spelling` on responses.** When typo correction is enabled for the caller, v1 analyze
  responses, v2 search responses and the v2 SSE terminal event carry `spelling`. It is absent
  otherwise. See [docs/architecture/typo-correction.md](../architecture/typo-correction.md).

## v1 endpoints

```text
POST /api/v1/search/analyze      GET /api/v1/search/stream/{requestId}
POST /api/v1/ai/chat             GET /api/v1/health[/live|/ready]
GET  /api/v1/version
```

In authenticated deployments, v1 endpoints other than health and version are refused with
`409 TENANT_API_REQUIRED`, because v1 has no tenant. Use v2 there.

## Migration from v1 to v2

| v1 | v2 |
|---|---|
| `POST /api/v1/search/analyze` | `POST /api/v2/search` (Commerce) or `POST /api/v2/domains/{domain}/search` |
| `GET /api/v1/search/stream/{requestId}` (subscribe after the POST) | `POST /api/v2/search/stream` (one call, finite stream) |
| no tenant, no authentication | bearer credential; tenant from the credential |
| `ApiError` (7 fields) | `ApiError` plus `category` and `retryable` |

Changes within v2 for 1.0 are additive:
- progress events gained `sequence`, `terminal`, `error` and `traceId`;
- errors gained `category` and `retryable`;
- new endpoints are `GET /api/v2/version` and `DELETE /api/v2/requests/{requestId}`.

Behaviour changes in 1.0:
- **v2:** a search `requestId` must follow the request-ID rule above.
- **v1 and v2:** an upstream REST error (for example OpenSearch answering 404) is now reported as
  `502` with a generic message (`DEPENDENCY_UNAVAILABLE` on v2, `INTERNAL_ERROR` on v1) instead of
  echoing the upstream status and client details.

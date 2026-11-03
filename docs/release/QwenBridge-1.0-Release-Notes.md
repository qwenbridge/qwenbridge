# QwenBridge 1.0: Release Notes

> **Release status:** see `QwenBridge-1.0-Release-Decision.md`. These notes describe what is
> implemented and verified on `main`, released as the candidate `1.0.0-RC1` (tag `v1.0.0-RC1`).
> They claim nothing beyond the tests and master runs cited. 0.9.1 is deprecated.

## Summary

QwenBridge 1.0 turns the earlier single-purpose search bridge into a domain-neutral platform:
- a Universal Intelligence Core with no dependencies other than Jackson;
- adaptive keyword, vector and hybrid retrieval;
- Commerce as an optional Domain Pack;
- connectors, AI providers, plugins, agents and policies as optional, versioned extensions;
- signed offline packs and licenses.

**Status of the 285 roadmap tasks:** 258 VERIFIED, 27 PARTIAL, none missing. Each PARTIAL item is
listed under *Known limitations*.

## What is in 1.0

| Area | Highlights | Documentation |
|---|---|---|
| **Core architecture** | Pure-Java Core (`qwenbridge-core`): request context, language detection, intent, grounded extraction, constraint validation, threat detection, central policy, confidence and evidence | `docs/architecture/overview.md`, ADRs 0001–0024 |
| **QueryIR and Schema Registry** | Universal, domain-neutral QueryIR 1.0 (1.1 adds the optional typo `correction`, QB-291, and multilingual `expansion`, QB-286) with versioned domain extensions; in-memory schema registry with ownership | ADRs 0015, 0016, 0047 |
| **Retrieval** | Keyword, vector and hybrid (RRF fusion), adaptive planning, reranking with candidate limits, zero-result recovery | ADRs 0025–0033 |
| **Domain Packs** | One contract for Commerce, Library and new domains: schemas, QueryIR extensions, domain policies; activate and deactivate cleanly | `docs/architecture/packs.md`, ADR 0047 |
| **Connectors** | Core Connector SPI with lifecycle, capabilities, configuration with secret references, timeouts and circuit breakers. Elasticsearch, OpenSearch, PostgreSQL and MongoDB, each packaged as an optional Connector Pack | ADRs 0037–0039, 0047 |
| **AI providers** | Chat, embedding and reranking SPIs; model identity; capability checks before execution; explicit, labelled fallback; vector spaces never mixed | `docs/architecture/ai-providers.md`, ADR 0048 |
| **Plugins and agents** | Plugin SDK 1.0 with validated manifests. Agents act only through tools authorized as the caller; prompt injection and delegation cannot widen permissions | ADR 0046, `docs/security/plugin-isolation.md` |
| **Security and authorization** | One central policy engine (default deny, tenant, scope, approvals); Policy Packs restrict only, in a fixed evaluation order | ADRs 0023, 0049, `docs/security/policy-packs.md` |
| **Packs** | Local registry (memory, file or Redis store); signed, immutable versions; dependency, runtime and conflict checks; rollback with a report; offline `qbpack` distribution; revocation | ADRs 0050, 0052 |
| **Licensing and editions** | Offline Ed25519 licenses with typed validation, grace period, key rotation; capability-based entitlements; Community, Professional and Enterprise | `docs/security/licensing.md`, `docs/editions.md` (matrix is a proposal), ADR 0051 |
| **API and SDKs** | REST v2 (`api-error/2`), SSE `progress-event/2`, Java and TypeScript SDKs, web component | `docs/api/rest-api.md`, `docs/sdk/`, `QwenBridge-1.0-Compatibility.md` |

## Installation and configuration

- **Local stack:** `docker compose up` starts the model server (Ollama) and the optional cache
  (Redis). `--profile production` adds the application (database-free by default), and
  `--profile opensearch` adds the optional OpenSearch backend (QB-293).
- **Minimal installation:** no database, Redis, search engine, Shopify or license server is needed:
  - `qwenbridge.platform.store=file` with `qwenbridge.platform.file-dir`;
  - `qwenbridge.commerce.backend=memory`;
  - `qwenbridge.embedding.enabled=false` when no embedding model is available.

  `CleanStartTest` verifies this.
- **Optional extensions:**
  - Connector Packs are off until listed in `qwenbridge.connector-packs.enabled`;
  - Domain Packs can be kept inactive with `qwenbridge.domains.disabled`;
  - AI fallback is off until `qwenbridge.ai.fallback-providers` is set.
- **Production:** the `%prod` profile requires authentication. Set CORS origins explicitly (see
  *Known limitations*).

## Benchmark results

- **Section 3 laptop baselines** (`evaluation/benchmarks/2026-11-01/`,
  `docs/evaluation/benchmark-results-2026-11-01.md`):

  | Workload | Result |
  |---|---|
  | Cached analysis, 1,000,000 requests at 250 VUs | 2,622 req/s, p95 119 ms, 0 errors |
  | Keyword retrieval | Saturates between 1,200 and 1,600 req/s on one 1 GiB container |
  | Vector and hybrid retrieval | About 55 req/s, limited by embeddings |
  | Model analysis | About 0.3 req/s on a single-slot local Ollama |

- **Final release gate:** see `QwenBridge-1.0-Section4-Report.md`.

These are laptop measurements. A production environment needs its own baselines.

## Migration

| From | To | How |
|---|---|---|
| v1 analyze | v2 | `docs/api/rest-api.md`, "Migration from v1 to v2". In authenticated deployments, v1 is refused with `409 TENANT_API_REQUIRED` |
| Legacy plugins | Plugin SDK 1.0 | Add a `META-INF/qwenbridge/plugin.json`. The legacy interface keeps working |
| Unsigned or hand-built pack artifacts | `qbpack` packages | `docs/architecture/pack-distribution.md` |
| Licenses | — | Version 1 format, unchanged; existing valid licenses keep working |

## Known limitations

- **Release blockers:** listed in `QwenBridge-1.0-Release-Decision.md`.
- **PARTIAL tasks (27):**

  | Section | Tasks |
  |---|---|
  | Section 1 | QB-006, 013, 017, 018, 026, 028, 031, 047, 053, 061, 070, 083 |
  | Section 2 | QB-086, 124, 129, 136 |
  | Section 3 | QB-148, 160, 166, 182, 184, 197, 206, 207, 208 |
  | Section 4 | QB-273 (no SDK licensing API; SDKs see standard 403 errors); QB-281 (NVD dependency scan blocked offline) |

  Each item's gap is described in its section report.
- **Not in 1.0:**
  - no sandbox for plugin or pack code;
  - Commerce search still runs on its built-in backends rather than Connector Packs (FU-12);
  - the server does not run QueryIR extension validation on every Commerce request;
  - no online revocation list (trust changes need a restart);
  - model versions are configured, not discovered.
- **Hardening backlog:** `docs/roadmap/QwenBridge-1.0-Followups.md` (FU-01 – FU-22), measured
  findings with priorities.

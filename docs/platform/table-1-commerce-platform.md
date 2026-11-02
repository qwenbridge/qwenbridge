# Table 1 — Commerce and Platform

> **Historical record.** It describes the repository at the time it was written. Modules and connectors it mentions may have been removed since: QB-287 removed the Elasticsearch, PostgreSQL, MongoDB and Shopify connector modules and `examples/` (ADR 0058). For the current state, start at [docs/README.md](../README.md).

Authorized scope: phases 14, 18, 20, 21, 23, 25, 28, 29, 32, 33, 35, 36, 37, 38,
39, plus expanded language/Filterless acceptance tests. Server: Quarkus/CDI;
all Java artifacts: Java 25. Existing uncommitted work must be preserved.

Status means **pending**, **in progress**, **implemented**, or **verified**.
Verified requires recorded acceptance evidence; compiling alone is insufficient.
External merchant acceptance remains separate from independent local protocol tests.

| Batch | Phases | Acceptance | Status |
|---|---|---|---|
| 1 | 14 + languages | Product/variant/money/stock/source/relationship model, ingestion normalization; diverse visible language corpus including Hindi and Malay | Implemented; final verification running |
| 2 | 32, 33 | Shared domain and connector contracts, two independent domains, capabilities/configuration/secret references/resource closure | Implemented; final verification running |
| 3 | 36, 21, 35 | Signed compatible packages, dependencies, install/activate/disable/upgrade/rollback; executable Commerce Pack; ordered tenant/domain policies and conflict evidence | Implemented; final verification running |
| 4 | 20, 18 | Owned expiring sessions independent of SSE; controlled zero-result proposals; explicit acceptance before changing hard constraints | Implemented; final verification running |
| 5 | 23, 25 | Independent Elasticsearch/PostgreSQL/MongoDB connectors; typed constraints, safe parameters, pagination/capabilities and live backend acceptance | Implemented; final verification running |
| 6 | 28, 29 | Web Component and Playground using REST/SSE, results/questions/errors, RTL and keyboard/browser acceptance; merchant connection when configured | Implemented; final verification running |
| 7 | 37, 38 | Signed expiring tenant licenses, enforced server capabilities, edition matrix and upgrade/downgrade tests | Implemented; final verification running |
| 8 | 39 | Publisher/package metadata, signed validation, local publication and download workflow with access control | Implemented; final verification running |

## Working checkpoint

- Baseline: 642 server tests, 39 Java SDK tests, 36 TypeScript tests; previous
  complete acceptance run `20261031-094646` is historical evidence, not validation
  of this table's new changes.
- Start with the catalog contract and source normalization; preserve existing
  V2 request/response compatibility and reference product IDs.
- Run focused meaningful tests per batch, then full local/live suites and staged
  performance at integration milestones. Exercise additional backends sequentially
  to keep the existing Docker Desktop resource budget viable.
- No merchant credentials are currently configured. Do not manufacture live
  Shopify acceptance, silently skip mandatory tests or publish externally.
- Update this checkpoint with implemented files, commands, results and next work.

## Current evidence and checkpoint

- Catalog normalization and the shared corpus are implemented. The canonical corpus
  now contains 31 visible cases over 14 languages plus mixed input, shared by JUnit
  and k6. Hindi/Malay/Indonesian scenarios are included.
- Independent Domain/Connector/Policy SDK contracts and Commerce/Library modules
  compile on Java 25. External signed JAR tests exercise upgrade, rollback,
  dependencies, policy conflicts and live socket resource closure after a lease.
- Sessions, explicit alternatives, signed licenses/editions and the local marketplace
  have focused acceptance tests. Quarkus API tests exercise owner/tenant isolation,
  permissions and finite session SSE; Chromium covers five shopper/Playground flows.
- Live authenticated Elasticsearch 8.19.23, PostgreSQL 16.15 and MongoDB 8.0.34
  tests pass. English and Hindi traverse actual model analysis, compilation and
  each backend and return only the reference product. A mixed Hindi/English
  negation bug found by this test was corrected; cache generation is now v10.
- Redis persistence/CAS tests use independent stores and concurrent updates.
- Java SDK now includes typed session, consent, cursor, timing and policy contracts;
  TypeScript and the Web Component use the same versioned paths.
- `scripts/qwenbridge-final-verification.sh` now prepares all backend fixtures,
  runs browser checks and eight k6 workloads including session/consent requests.
- Final complete run is in progress. Record exact counts/results below before
  changing these statuses to verified. Live Shopify remains unverified without
  merchant configuration. Table 2 has not been started.

See [API and operational scope](table-1-api.md).

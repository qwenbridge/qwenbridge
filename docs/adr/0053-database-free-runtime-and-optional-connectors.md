# ADR 0053: Database-Free Runtime, Optional Connectors and Redis as a Cache

## Status

Accepted (QB-293). It supersedes the "default backend stays OpenSearch" note in ADR 0030 and the
Redis default of the platform store (ADR 0050).


> **QB-287 (ADR 0058):** superseded in part. The Elasticsearch, PostgreSQL, MongoDB and Shopify connector modules described here were removed from the repository; the generic contracts (SearchProvider, Connector SPI, CatalogConnectorFactory) remain the extension point for future adapters.

## Context

Core was already database-independent, and connectors already lived in their own modules.
However, the default runtime still leaned on external services:
- the default Compose stack started OpenSearch, and the app waited for it;
- Commerce search defaulted to the OpenSearch backend;
- the v1 pipeline sent every retrieval to OpenSearch, because the backend was taken from the
  model's analysis;
- platform state (licenses, packs, sessions) defaulted to Redis outside tests;
- Commerce evidence and analytics failed when Redis was down;
- a Shopify feed mapping shipped in the Commerce pack, and the server depended on the Shopify
  module at compile scope.

## Decision

1. **In-process defaults.**
   - `qwenbridge.search.backend=in-memory` and `qwenbridge.commerce.backend=memory`.
   - Connectors are selected explicitly, and the retrieval backend is configuration, never a model
     output.
   - The `%test` profile keeps OpenSearch, so the existing suites test that connector path
     unchanged.
2. **Compose profiles.** OpenSearch moves to an `opensearch` profile, with an optional
   `depends_on`. The app uses in-process backends and file-backed state by default. The
   verification enables `production,opensearch` explicitly.
3. **Redis is a cache.**
   - Platform state defaults to memory (with a warning) and to the file store in Docker; Redis
     only when chosen explicitly.
   - Evidence and analytics fall back to bounded in-process stores when Redis fails.
   - The Quarkus built-in Redis readiness check is off; Redis is a reported dependency.
4. **Shopify is test-only.**
   - Its mapping lives in the Shopify module, registered through `ServiceLoader`.
   - The server depends on the module in test scope only.
5. **Production validator.** It no longer requires the OpenSearch or Redis settings, and it runs
   in the `prod` profile (audit finding S1b).
6. **Guards and evidence.** `ArchitectureNeutralityTest` and the master step "QB-293:
   database-free runtime".

## Consequences

- **Durable state needs a store.** A plain `java -jar` keeps platform state in memory; use the
  file store for durability without services.
- **Deployments that relied on the old OpenSearch defaults** must set
  `qwenbridge.search.backend=opensearch` and `qwenbridge.commerce.backend=opensearch`.
- **Existing Docker state:** licenses or packs stored earlier in the local Docker Redis are not
  migrated to the new file store.
- **v1 without OpenSearch:** v1 analyze has only the keyword-only demo provider, and refuses
  constrained queries with a typed 400. v2 is the full database-free path.
- **Connector libraries stay packaged.** The connector modules and their drivers remain in the
  server distribution as optional, inactive libraries. Removing them from the default
  distribution would need a separate packaging decision.

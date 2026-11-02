# ADR 0043: Developer Playground, Datasets, Service-Free E2E and Shopify Isolation

## Status

Accepted (QwenBridge 1.0, Section 3 Phase 29: QB-199 – QB-206)


> **QB-287 (ADR 0058):** superseded in part. The Shopify sandbox test and connector modules described here were removed from the repository; the generic contracts (the service-free E2E profile) remain the extension point for future adapters.

## Context

- **Playground.** It printed raw JSON. It could not compare strategies, and it did not show which
  constraints came from the user's words.
- **Data.** There was no reusable synthetic dataset; test catalogs were inline in tests.
- **Redis.** v2 search needed Redis for evidence and analytics, so every end-to-end test mocked
  them.
- **Shopify.** Its GraphQL transport lived in the server, and catalog synchronization branched on
  `kind.equals("shopify")`.

## Decision

1. **Playground (QB-199 – QB-202).** The page shows only recorded data or real follow-up
   executions.
   - **Constraints** are labelled explicit (a quote from the input, with offsets) or inferred (with
     their evidence source).
   - **Retrieval plan.** The recorded plan is shown, and its absence for apiVersion 2 is stated.
   - **Confidence.** It is labelled by its `calibrated` flag, and product scores are labelled
     relevance scores.
   - **Strategy comparison.** The same query is executed with KEYWORD, VECTOR, HYBRID and the planned
     contract, and refusals are shown as such.
   - **Connection options** include mode, contract and backend.
2. **Datasets (QB-203).** Two versioned synthetic datasets carry named edge cases and expected
   results with reasons:
   - `commerce-demo` 1.0 (server resource; Playground and E2E);
   - `library` 1.0 (Core test resource).

   `qwenbridge.demo.datasets` loads Commerce datasets at startup, and refuses any backend other
   than `memory`.
3. **Service-free operation (QB-205).**
   - **In-process storage.** With `qwenbridge.platform.store=memory`, Commerce evidence and
     analytics use a bounded in-process TTL store (`LocalTtlStore`), and Redis is not resolved.
   - **End-to-end test.** `ServiceFreeE2ETest` runs complete workflows with every service
     unreachable, and a controlled local model (an Ollama-compatible stub). The real client,
     parser, grounding and safety gate run.
4. **Shopify isolation (QB-204, QB-206).**
   - **SPI.** The catalog connector SPI (`CatalogConnector`, `CatalogConnectorFactory`) moved to the
     Commerce pack.
   - **Module.** The Shopify transport is the optional module `qwenbridge-connector-shopify`.
   - **Server.** It keeps only `http-json`, finds other kinds through `ServiceLoader`, and uses
     `completeSnapshot()` instead of a kind check. `ShopifyIsolationTest` forbids Shopify code in
     the server.
   - **Sandbox test.** `ShopifySandboxTest` runs only when sandbox credentials are set.
5. **Live evidence.** A new master step runs the strategy comparison against the live server.

## Consequences

- **Semantics.** The demo dataset documents a real difference between apiVersion 2 and
  `commerce-search/1.0` exclusion semantics: missing fields are excluded by the first and kept by
  the second.
- **Memory store limits.** With the memory store, evidence (1 h) and stored searches (10 min) are
  per instance and lost on restart.
- **Unverified.** The Shopify sandbox test has not been run against a real store; no sandbox was
  available (H7).
- **Model still required.** End-to-end coverage without a model is limited to the fail-closed path;
  full workflows use the controlled local model.

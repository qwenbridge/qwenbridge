# ADR 0031: Commerce Search Contract 1.0: Interpretation over QueryIR and Planned Retrieval

## Status

Accepted (QwenBridge 1.0, Section 2 Phase 13 part 2: QB-085 – QB-090)


> **QB-287 (ADR 0058):** superseded in part. The Elasticsearch, PostgreSQL and MongoDB backends described here were removed from the repository; the generic contracts (the backend-neutral contract) remain the extension point for future adapters.

## Context

- **Strategy came from the client.** The Commerce search request (apiVersion 2)
  let the client pick KEYWORD, VECTOR or HYBRID.
- **The universal QueryIR was not used.** Commerce compiled the model's
  constraints straight into each backend's query language.
- **Ignored understanding.** Subjective wording and multi-product requests were
  not used.
- **Opaque responses.** A response did not say which strategy ran or whether
  anything was degraded.
- **NE disagreed.** Commerce's "not equal" required the field to exist, while
  the neutral semantics (ADR 0025) keep documents that lack it.

## Decision

1. **Versioned contract (QB-085).** A request may set
   `contract: "commerce-search/1.0"`. Without it the request behaves exactly as
   in apiVersion 2, and the response has no new keys (they are omitted when
   null). Under 1.0:
   - `mode` must be absent or `AUTO`; a client strategy is refused (400).
   - An unknown contract version gets 400.
   - Pages reach at most 30 results deep.

   The same endpoints (`/api/v2/search`, `/stream`, `/{id}/refine`) carry both
   contracts.
2. **Interpretation layer (QB-086).** `CommerceInterpretation` expresses the
   query as the universal QueryIR and maps it onto the Commerce schema.
   - **Understanding.** Intents, retrieval needs and the decomposition come from
     the Core intent analyzer; semantic signals from Core extraction.
   - **Constraints.** The model's evidence-verified constraints become HARD,
     USER_TEXT constraints with their spans. Refinement-retained ones become
     CONTEXT constraints. Core extraction's own constraint proposals are not
     added, so a query has one source of filters.
   - **Canonical values.** Values are mapped to the catalog's canonical terms
     with the same vocabulary that normalizes documents (ADR 0029): `nike` →
     `Nike`, `Noir` → `black`, `Sneakers` → `shoes`, `eur` → `EUR`.
   - **Validation.** Each scope is validated by the Core `ConstraintValidator`
     against the pack's schema with the approved mapping rules. A blocking
     assessment returns `CLARIFICATION_REQUIRED` and is never weakened. The
     apiVersion 2 consistency checks still run per scope.
3. **Planned retrieval (QB-087).** `CommercePlanning` hands the validated query
   to the Core `AdaptivePlanner`.
   - **Providers.** The candidates are the selected backend's keyword side and,
     where one exists in the configured embedding space, its vector side:
     - OpenSearch: kNN with the neutral filter inside the kNN search (Lucene
       engine);
     - the in-memory backend: an in-memory vector index fed by the
       `embedding` field that catalog sync writes.

     Elasticsearch, PostgreSQL and MongoDB are keyword-only on the SPI, so a
     semantic need there is reported as `SEMANTIC_UNAVAILABLE`.
   - **Execution.** The Core `PlanExecutor` runs the plan through the unified
     SPI and re-checks every result against the HARD filter. Retrieval text is
     the catalog-language text of the Commerce flow; a structured-only query
     browses by filter.
   - **NE semantics.** Exclusions use the neutral semantics: "not refurbished"
     keeps a product that states no condition. This applies to 1.0 only;
     apiVersion 2 results are unchanged.
4. **Subjective preferences (QB-088).** Words such as comfortable, lightweight
   and elegant (Core extraction's signal lexicon, which covers English, Swedish
   and Persian) become semantic signals.
   - They create a semantic retrieval need and are reported in
     `interpretation.semanticSignals`.
   - They never become filters. No threshold is invented; for example,
     "lightweight" is not turned into a weight limit.
5. **Decomposition (QB-089).** When the Core analyzer splits the query into two
   or more parallel subqueries ("black Nike shoes and a white backpack"):
   - Each subquery is a scope with the constraints whose evidence lies inside
     its part of the text. Constraints stated outside every part apply to all.
   - Each scope is validated, planned and executed on its own, so two colours
     no longer conflict.
   - The response carries `subqueries[]`, each with its objective, constraints,
     retrieval and results. The top-level results are empty, and paging is not
     offered.
   - Subquery text is matched as written. When the query was translated, this
     is flagged as `SUBQUERY_TEXT_UNTRANSLATED`.
6. **Response contract (QB-090).** Under 1.0 the response adds three blocks:
   - **`contract`.**
   - **`retrieval`.** It reports:
     - `requested` and `executed`, the requested and executed strategies;
     - planner `reasons`;
     - runtime `degradations`;
     - `degraded`, true whenever the requested strategy or a planned stage did
       not run;
     - `removedByValidation`;
     - `providers`;
     - `warnings`.
   - **`interpretation`.** It reports:
     - QueryIR version, intents and retrieval needs;
     - semantic signals;
     - a validation status for every constraint, with its scope.

   New statuses are `UNSUPPORTED` (no provider can serve the validated query;
   the requirements are listed) and `PARTIAL` (a subquery could not run).
7. **Tests.**
   - **Planner path, offline.** In-memory backend with real vectors from a
     deterministic trigram embedder. Covers:
     - structured browse with canonical values and tenant isolation;
     - signals giving hybrid retrieval without filtering;
     - an embedding outage and a backend without vectors, both degrading
       visibly to keyword;
     - neutral NE;
     - decomposition scoping;
     - the paging window.
   - **REST, offline.** Covers:
     - the 1.0 response blocks;
     - the NE difference against apiVersion 2 on the same catalog;
     - an unchanged v2 response shape;
     - refusal of client strategies, unknown versions and deep pages;
     - a multi-product query answered per subquery.
   - **Live.** OpenSearch kNN on the verification run's seeded BGE-M3 index
     with live Ollama query vectors. It checks the semantic match, the filter
     inside kNN, tenant isolation and refusal of a foreign embedding space.

## Consequences

- **Decomposition source.** Decomposition depends on the Core analyzer's
  separators ("and a/an", "then", the Swedish and Persian equivalents). A list
  without them stays one query.
- **Paging window.** The 1.0 window is 30 results, because each provider call
  fetches three times the page window and connectors cap at 100.
- **Vectors on other backends.** Elasticsearch, PostgreSQL and MongoDB need a
  vector side on the SPI before 1.0 can run hybrid retrieval on them.
- **Temporal constraints.** `availableAt` constraints are validated, but the
  filter compiler cannot compile temporal values yet. Under 1.0 they return
  `UNSUPPORTED` rather than being dropped.
- **Rule location.** The interpretation's mapping rules still live in the
  server (`DomainMappings`) until the pack vocabulary SPI ships them with the
  pack (QB-053).

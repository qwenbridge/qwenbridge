# ADR 0027: Filter Compilation and Adaptive Retrieval Planning

## Status

Accepted (QwenBridge 1.0, Section 2 Phase 15: QB-099 – QB-106)

## Context

Before this change, the retrieval strategy came from model output
(`SearchDecision`) or from the client (`mode`). There were three plan shapes:
the v1 `ExecutionPlan`, the Commerce `Plan`, and the search-mode flags. Filters
were compiled straight into each backend's DSL inside `CommerceCompiler`.

## Decision

1. **Filter compiler (QB-100).** `FilterCompiler` accepts only a
   `ValidatedQuery` and compiles its unscoped HARD constraints into the neutral
   `Filter` AST:

   | Constraint | Filter |
   |---|---|
   | EQ, NE, LT, LTE, GT, GTE | Compare (ordering requires a number) |
   | BETWEEN | Bounded compares |
   | IN / NOT_IN | In / Not(In) |
   | CONTAINS / NOT_CONTAINS | Contains / Not(Contains) |
   | EXISTS / NOT_EXISTS | Exists / Not(Exists) |

   Money and quantities become exact numbers; currency and unit were already
   separated by the semantic mapper.

   A HARD constraint that cannot be compiled is never dropped. The whole query
   becomes `NotCompilable` with the constraint ids. Temporal values are the
   current case, until connectors define a date representation.
2. **Plan contract (QB-099).** `RetrievalPlan` (version 1.0) holds:
   - the tenant and the strategy;
   - the residual text (the query without the spans of compiled constraints);
   - the compiled filter and the SOFT preferences, which are for ranking only;
   - topK and a budget (timeout and maximum provider calls);
   - ordered stages: EMBED_QUERY, KEYWORD_RETRIEVAL, VECTOR_RETRIEVAL, FUSE and
     VALIDATE_HARD_CONSTRAINTS;
   - an optional fallback strategy and the reason codes.

   Every plan re-validates HARD constraints and states its reasons.
3. **Adaptive planner (QB-101, QB-102).** `AdaptivePlanner` derives needs from
   the QueryIR:
   - **Exact:** EXACT hints (identifiers).
   - **Lexical:** LEXICAL hints, or no semantic need.
   - **Semantic:** SEMANTIC hints or semantic signals.
   - **Structured only:** no residual text.

   The rules:
   - Exact identifiers → keyword: vectors only add noise for codes.
   - Lexical and semantic → hybrid.
   - Structured only → filter browse. The keyword index now treats a query
     without terms as "every document that satisfies the filter, ordered by
     id".

   The strategy never comes from the client or from model output.
4. **Capability negotiation (QB-103).** A provider is usable only if it
   supports every filter feature, the mode, the candidate count and, for
   vectors, the current embedding space.
5. **Typed outcomes (QB-104).**
   - `PlanOutcome` is `Planned` or `Unsupported` (requirements and reasons).
   - A desired strategy that cannot run falls back to a usable one, with
     SEMANTIC_UNAVAILABLE, VECTOR_REINDEX_REQUIRED or CAPABILITY_MISMATCH
     recorded.
   - `PlanExecutor` runs the plan through the hybrid retriever, so runtime
     degradations are recorded. It then removes, and counts, any result that
     violates the HARD filter.
6. **Policies (QB-105).** `PlanningPolicy` has an objective (LATENCY, BALANCED
   or QUALITY), a timeout and a maximum number of provider calls. Expected
   latencies are operator-configured estimates (`CandidateProvider`), not
   measurements. The budget can only reduce the number of calls, which is
   recorded as BUDGET_LIMITED.
7. **Test matrix (QB-106).** A 14-row matrix covers hints, objective, provider
   availability, embedding space, call budget and latency budget, with the
   expected strategy and reason. End-to-end tests cover:
   - real extraction through the validation gate, the compiler, the plan and
     execution on a synthetic catalog;
   - exclusions and price bounds enforced;
   - structured browse;
   - a lying provider caught by re-validation;
   - a provider that lacks NOT support refused;
   - hybrid execution;
   - an uncompilable temporal constraint refusing the query.

## Consequences

- Connectors must translate the `Filter` AST themselves (QB-100 connector side)
  and implement the unified SPI (QB-107). This is Batch 15b.
- The v1 pipeline and Commerce move onto the planner when the Commerce layer is
  rebuilt over the universal request (QB-085 – QB-091).

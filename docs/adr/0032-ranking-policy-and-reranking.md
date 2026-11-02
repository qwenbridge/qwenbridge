# ADR 0032: Versioned Ranking Policy, Deterministic Baseline, One Reranker SPI

## Status

Accepted (QwenBridge 1.0, Section 2 Phase 17: QB-117 – QB-124)

## Context

There were three ranking mechanisms:

| Mechanism | Path | Problem |
|---|---|---|
| A weighted policy with hard-coded constants (`DefaultRankingPolicy`) | v1 pipeline only | Unnamed and unversioned |
| A query-term reranker | v1 pipeline | Its own interface |
| A model permutation reranker (`CommerceReranker`) | Commerce | Its own interface; overwrote the backend score (audit, `CommerceReranker:129-130`) |

There were also these gaps:

- No business-rule layer existed.
- Results were validated before ranking but not after.
- Score semantics were undocumented.

## Decision

All of this lives in Core (`io.qwenbridge.core.ranking`) and has no dependencies.

1. **Versioned policy (QB-117).** `RankingPolicy` holds an id, a version, three
   weights, business rules and a rerank window. Every ranking reports
   `id@version`.
   - **Weights** are finite and within [0, 10], and retrieval always counts.
   - **Business weight** is at most the retrieval weight.
   - **Rules** number at most 64, each with a unique id.

   The server reads `qwenbridge.ranking.policy.*` and
   `qwenbridge.ranking.business-rules` (JSON) once at startup. Invalid
   configuration stops startup. The default is `qwenbridge-baseline@1.0`:
   retrieval weight 1, preferences 0.5, no business rules, rerank window 20.
2. **Deterministic baseline (QB-118).** `BaselineRanker` scores each result
   with three contributions:
   - **Retrieval:** weight × (n − rank + 1) / n.
   - **SOFT preferences:** weight × satisfied / total. Preferences rank and
     never filter.
   - **Business rules:** weight × clamp(Σ boosts, −1, 1).

   Ties break by retrieval rank, then by id. The order never depends on the
   input order, the time or the network, so every path can apply it.
3. **One reranker SPI (QB-119, QB-245).** `Reranker` takes ids and fields and
   returns an order, optionally with its own scores. `RerankStage` guards every
   reranker:
   - It offers only the window.
   - It requires a complete permutation.
   - It turns a timeout, an exception or an invalid answer into the unchanged
     baseline with a status: APPLIED, DISABLED, TOO_FEW, TIMEOUT, FAILED or
     INVALID_PERMUTATION.

   Two implementations exist: the model and plugin reranker
   (`CommerceReranker.asReranker`, at most four concurrent calls, with
   prompt-injection quarantine as before) and the v1 query-term reranker.
4. **Scores preserved (QB-120).**
   - **In the pipeline,** every item keeps its retrieval score (value, kind,
     scorer), its baseline score and its components. A reranker adds a RERANK
     component and its position; it changes order, not scores.
   - **On the apiVersion 2 rerank path,** hits now also carry
     `metadata.retrievalScore`, `rerankPosition` and `reranker`. Before, the
     backend score was lost.
5. **Business rules (QB-121).** A `BusinessRule` is a condition plus a boost in
   [−1, 1].
   - Rules only reorder results that already passed authorization, the tenant
     boundary and every HARD constraint. They never add, remove or reveal a
     result.
   - The final re-validation removes a violating result, whatever its boost.
6. **Re-validation after ranking (QB-122).** `RankingPipeline` checks every
   final item:
   - it satisfies the HARD filter;
   - it was retrieved;
   - it appears only once.

   Anything else is removed and counted (`removedByValidation`).
7. **Score semantics (QB-123).** `ScoreComponent` documents raw values and
   contributions per kind. The final score is an ordinal ranking value,
   comparable only within one response. It is not a probability, a confidence
   or a relevance grade, and raw scores of different kinds are never added
   together.
8. **Quality and determinism (QB-124).** `RankingMetrics` provides nDCG@k, MRR
   and precision@k, with hand-computed checks. A labelled seed set covers three
   queries with graded judgments:
   - For "preferably black", nDCG@3 is 0.934 with the baseline against 0.431
     for the retrieval order.
   - The second query improves too.
   - A query without preferences keeps the retrieval order exactly.
   - Rankings are identical over repeated runs and input shuffles.

   This is contract-level evidence on judgments written for the test (audit
   H7). It is not a claim about quality on real traffic.

### Where it applies

| Path | Ranking |
|---|---|
| `commerce-search/1.0` | Full pipeline: baseline, optional reranker on `rerank: true`, re-validation. The response reports `retrieval.rankingPolicy` and `retrieval.reranking`. Hits carry the position, retrieval rank and score, score components and rerank position. |
| v1 pipeline | Existing weighted ranker, now named `qwenbridge-v1-weighted@1.0` in each hit's metadata |
| apiVersion 2 Commerce | Backend order (`backend-baseline`) as before, plus the score-preserving rerank |

## Consequences

- **Rerank at paging time.** Model reranking is not deterministic, so pages of
  one reranked query may differ between calls. The baseline alone is
  deterministic.
- **v1 weights.** The v1 weighted policy keeps its constants. Changing them now
  requires a new version id. Moving v1 onto the Core baseline is part of the
  v1-to-planner migration.
- **v2 order.** apiVersion 2 does not apply the Core baseline, so its result
  order stays the backend's.

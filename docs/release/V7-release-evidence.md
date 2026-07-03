# V7 Release Evidence

## Release Scope

V7 delivers safe AI token streaming and the first reproducible retrieval-quality
evaluation layer for QwenBridge.

## Verification Command

```bash
mvn clean test
```

## Latest Verification

```text
Tests run: 318
Failures: 0
Errors: 0
Skipped: 3
BUILD SUCCESS
```

## Evidence Areas

| Area | Evidence |
| --- | --- |
| AI token streaming | `ai.token`, `ai.completed`, and `ai.failed` SSE events |
| Stream lifecycle | disconnect cancellation and terminal lifecycle tests |
| Stream safety | max duration, token count, and event count limits |
| Ranking | ranking policy and execution-path integration |
| Reranking | safe reranking service and execution-path integration |
| Evaluation metrics | Precision@K, Recall@K, MRR, nDCG@K |
| Quality gate | threshold policy with deterministic pass/fail result |
| Benchmark input | CSV loader and relevance benchmark fixture |
| End-to-end evaluation | benchmark runner report |
| Documentation | API, ADR, benchmark, quality evidence, and V7 roadmap |

## Release Decision

V7 is ready for merge after final local verification and review.

## Known Scope Boundaries

V7 provides the foundation for retrieval quality measurement. It does not claim
production-grade benchmark scale, learned ranking models, CI quality gates, or
formal load/performance evidence.

Those items remain planned work after V7.

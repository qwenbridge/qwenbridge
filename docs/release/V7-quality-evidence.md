# V7 Quality Evidence

## Status

In progress.

## Scope Covered

V7 introduces the first reproducible quality-evaluation foundation for
QwenBridge search quality.

Implemented capabilities:

- Ranking score policy
- Ranking applied to search results
- Ranking integrated into execution
- Reranking SPI
- Safe reranking service with fallback behavior
- Reranking integrated into execution
- Retrieval quality metrics
- Benchmark CSV loader
- Benchmark evaluator service
- Evaluation threshold policy
- End-to-end benchmark runner

---

## Metrics Implemented

| Metric | Implemented |
| --- | --- |
| Precision@K | Yes |
| Recall@K | Yes |
| MRR | Yes |
| nDCG@K | Yes |

---

## Quality Gate

Current default thresholds:

| Metric | Minimum |
| --- | --- |
| Precision@K | `0.60` |
| Recall@K | `0.60` |
| MRR | `0.60` |
| nDCG@K | `0.70` |

---

## Reproducibility

Command:

```bash
mvn clean test
```

Latest observed result:

```text
Tests run: 318
Failures: 0
Errors: 0
Skipped: 3
BUILD SUCCESS
```

---

## Benchmark Dataset

Current fixture:

```text
src/test/resources/evaluation/relevance-benchmark.csv
```

Current fixture size:

```text
2 queries
6 labels
graded relevance scale: 0..3
```

---

## Baseline Interpretation

The current dataset is a small validation fixture, not a statistically complete
retrieval benchmark.

It proves that:

- benchmark data can be loaded reproducibly
- metrics are calculated deterministically
- quality thresholds produce pass/fail output
- benchmark runner produces a release-friendly report object

A larger benchmark dataset is required before claiming production-grade search
quality.

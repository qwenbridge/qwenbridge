# Retrieval Quality Benchmark

## Purpose

This benchmark provides reproducible evidence for QwenBridge retrieval, ranking,
reranking, and quality-gate behavior.

It is intended for V7 release validation and future regression checks.

---

## Benchmark CSV Format

Benchmark files use CSV format:

```csv
queryId,query,documentId,relevance
q1,gaming mouse,doc-mouse-1,3
q1,gaming mouse,doc-mouse-2,2
q1,gaming mouse,doc-keyboard-1,0
```

### Columns

| Column | Meaning |
| --- | --- |
| `queryId` | Stable benchmark query id |
| `query` | Natural-language search query |
| `documentId` | Candidate document id |
| `relevance` | Human relevance label |

### Relevance Scale

| Value | Meaning |
| --- | --- |
| `0` | Not relevant |
| `1` | Weakly relevant |
| `2` | Relevant |
| `3` | Highly relevant |

---

## Metrics

### Precision@K

Measures how many of the top `K` retrieved documents are relevant.

```text
Precision@K = relevant documents in top K / K
```

### Recall@K

Measures how many known relevant documents were retrieved in the top `K`.

```text
Recall@K = relevant documents in top K / total relevant documents
```

### Mean Reciprocal Rank

Measures how early the first relevant result appears.

```text
RR = 1 / rank of first relevant result
MRR = average RR across queries
```

### nDCG@K

Measures ranking quality using graded relevance labels.

QwenBridge uses graded gain:

```text
gain = 2^relevance - 1
```

and logarithmic discount:

```text
discount = log2(rank + 1)
```

```text
nDCG@K = DCG@K / IDCG@K
```

---

## Quality Gate Thresholds

Current V7 defaults:

| Metric | Minimum |
| --- | --- |
| `precisionAtK` | `0.60` |
| `recallAtK` | `0.60` |
| `meanReciprocalRank` | `0.60` |
| `ndcgAtK` | `0.70` |

A benchmark report passes only when all metrics meet or exceed their thresholds.

---

## Pass / Fail Interpretation

A passing report means the current retrieval/ranking behavior satisfies the
configured minimum quality bar for the evaluated benchmark dataset.

A failing report means at least one quality metric dropped below the configured
threshold and must be investigated before release.

Failure examples:

```text
precisionAtK 0.5900 is below required minimum 0.6000
ndcgAtK 0.4000 is below required minimum 0.7000
```

---

## Reproducible Test Path

Run the complete quality benchmark foundation through Maven:

```bash
mvn clean test
```

Relevant test classes:

```text
io.qwenbridge.evaluation.metrics.RetrievalEvaluationMetricsTest
io.qwenbridge.evaluation.dataset.BenchmarkDatasetLoaderTest
io.qwenbridge.evaluation.service.DefaultRetrievalEvaluationServiceTest
io.qwenbridge.evaluation.policy.DefaultEvaluationThresholdPolicyTest
io.qwenbridge.evaluation.runner.DefaultBenchmarkEvaluationRunnerTest
```

Benchmark fixture:

```text
src/test/resources/evaluation/relevance-benchmark.csv
```

---

## Current Scope

This benchmark foundation validates the evaluation framework and quality-gate
logic. The current fixture is intentionally small.

Future V7+ work should expand it into a broader relevance dataset covering:

- keyword retrieval
- vector retrieval
- hybrid retrieval
- reranking behavior
- multilingual queries
- ambiguous queries
- low-recall edge cases

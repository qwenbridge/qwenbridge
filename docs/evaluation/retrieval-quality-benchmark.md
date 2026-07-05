# Retrieval Quality Benchmark

QwenBridge includes a reproducible retrieval-quality evaluation foundation.

## Goal

The benchmark provides a repeatable way to measure whether retrieval changes improve or degrade result quality.

## Dataset

The benchmark dataset lives at:

```text
qwenbridge-server/src/test/resources/evaluation/relevance-benchmark.csv
```

Each evaluation query contains expected relevance labels used by the evaluation runner.

## Metrics

The retrieval evaluation layer supports quality metrics such as:

- precision-oriented relevance checks
- ranked-result quality checks
- threshold-based pass/fail decisions
- benchmark report generation

## Gate policy

Quality thresholds are enforced by an evaluation threshold policy. A release should not claim retrieval-quality improvement unless benchmark evidence supports it.

## Usage

Run the Java verification suite:

```bash
mvn clean verify
```

Run release verification:

```bash
bash scripts/verify-release.sh
```

Benchmark-related tests are part of the server test suite.

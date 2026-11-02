# ADR 0044: Evaluation Framework and Measurement Definitions

## Status

Accepted (QwenBridge 1.0, Section 3 Phase 30, part 1: QB-207 – QB-215)

## Context

- **Quality data.** Quality evidence was a 6-row CSV and a 31-case Commerce corpus, with no vector
  or hybrid comparison.
- **Latency** came only from k6, on the client side.
- **Errors** were one `http_req_failed` rate, so rejected invalid input and server failures looked
  the same.
- **Resources and Redis.** Resource use was noted by hand; Redis was measured only through
  cache-level counters.

## Decision

1. **Metrics in Core** (`io.qwenbridge.core.evaluation`):
   - `RankingMetrics` (P@K, R@K, reciprocal rank, graded nDCG, negative false positives), checked
     against hand calculations;
   - `QualityEvaluation`, which runs any strategy over a judged dataset and aggregates by
     language, kind and domain.
2. **Versioned datasets.** `evaluation/datasets/multidomain-1.0.json` is a seed set; its judgment
   limits are stated in the file and in the docs.
3. **Real comparison.** `RetrievalQualityEvaluationTest` measures keyword, vector and hybrid with
   the configured embedding model and the platform's Core retrieval, on every master run. Results
   are files in `target/evaluation`, and no threshold is invented.
4. **Machine-checked capability matrix.** `evaluation/capability-matrix.json` is verified by
   `CapabilityMatrixTest`; missing coverage is an explicit gap.
5. **Server-side measurement.**
   - **Histograms:** percentile histograms for HTTP and `qwenbridge.*` timers, plus stage and total
     search timers.
   - **Counters:** `qwenbridge.api.errors{class=expected|operational}`, interruptions, and the
     `qwenbridge.api.inflight` gauge.
6. **Per-phase load report.** Each phase in `run-performance.py` records:
   - achieved RPS (separate from offered VUs) and duration;
   - the server error-class deltas;
   - sampled CPU, heap, GC, threads and container memory;
   - Redis command and keyspace deltas, or "not applicable";
   - the benchmark environment.
7. **Definitions.** `docs/evaluation/metrics.md` defines boundaries, units, sampling, denominators
   and error treatment.

## Consequences

- **Hybrid not best.** The first measured comparison shows hybrid is not best on this set, and that
  vector retrieval needs a threshold to answer "nothing"; both findings are documented.
- **Not authoritative.** Quality numbers are not production relevance claims, because the judgments
  are a single-annotator seed set (H7).
- **Approximation.** Redis amplification counts all clients.
- **Next.** The load, spike, soak, stress and outage runs and the regression gates (QB-216 –
  QB-224) build on these definitions and reports.

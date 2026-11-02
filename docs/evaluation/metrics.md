# Evaluation and performance metrics

These are the definitions behind every number QwenBridge reports (QB-209, QB-211 – QB-215). A
number without its definition, workload and environment is not evidence.

## Retrieval quality (QB-209)

Implementation: `io.qwenbridge.core.evaluation.RankingMetrics`, checked against hand-calculated
values in `RankingMetricsTest`.

**Judgments.** Grades run 0–3: 3 exact answer, 2 relevant, 1 partially relevant, 0 not relevant.
Unjudged documents count as 0. "Relevant" means grade ≥ 1.

| Metric | Definition | Reading it |
|---|---|---|
| Precision@K | relevant results in the first K ÷ K | Missing positions count as non-relevant. With few relevant documents per query it is capped (3 relevant at K=5 gives at most 0.6) |
| Recall@K | relevant results in the first K ÷ all relevant | Did we find what exists? |
| MRR | mean over queries of 1 ÷ position of the first relevant result (0 if none) | How soon the user sees something useful |
| nDCG@K | Σ (2^grade − 1) / log2(position + 1) over the first K, ÷ the same for the ideal order | 1.0 is the best possible order; grades make exact answers count more |
| Coverage | share of scored queries with a relevant result in the first K | |
| Negative false-positive rate | for queries with no relevant document: results returned in the first K ÷ K, averaged | 0 means it correctly returns nothing |

**Query handling.**
- Recall, MRR and nDCG are undefined for a query with no relevant document. Such "negative" queries
  are scored only by the false-positive rate.
- Only the `heldout` split is reported; `tuning` queries may be used to adjust ranking.

## Latency (QB-211)

| Measurement | Boundary | Source |
|---|---|---|
| Client end-to-end | k6 sends the request until the full response is received; includes the network and queueing | `http_req_duration` p(50)/p(95)/p(99) in each phase summary |
| Server HTTP | the server receives the request until the response is written | `http_server_requests_seconds` histogram |
| Search total | `CommerceSearchService` start to response assembly | `qwenbridge_search_duration_seconds{contract,status}` |
| Search stages | each recorded stage (`input-validation`, `analysis`, `translation`, `constraint-validation`, `embedding`, `execution`, `ranking`, `interpretation`) | `qwenbridge_search_stage_seconds{stage,contract}` |

**Histograms.**
- **Units:** seconds.
- **Buckets:** percentile histograms from 1 ms to 60 s (`LatencyHistograms`).
- **Percentiles:** computed over a window by the metrics backend, e.g.
  `histogram_quantile(0.95, sum by (le) (rate(qwenbridge_search_stage_seconds_bucket{stage="analysis"}[5m])))`.
- **Sampling:** every request is recorded; there is no sampling.

**Errors and timeouts.**
- k6 latency includes failed requests, and the error rate is always reported next to it.
- A request stopped by its deadline is recorded with the time it ran (≈ the deadline) and counted
  as an interruption.

## Throughput and concurrency (QB-212)

- **Workload model.** k6 runs a *closed* model: each virtual user (VU) sends its next request when
  the previous one finishes.
  - **Offered load** = the VU count (concurrency).
  - **Achieved throughput** = `achieved_rps`, which is k6's `http_reqs` rate over the phase.
  - These are reported separately; a configured VU count is never presented as throughput.
- **Active concurrency on the server:** `qwenbridge_api_inflight`, the v2 requests executing under
  a deadline, sampled during each phase (`inflight_max`).
- **Successful completion rate** = completed requests without error ÷ expected requests.
- **Saturation indicators:**
  - p95 rising faster than throughput;
  - `inflight_max` reaching the VU count;
  - Redis pool rejections, CPU near 100 %, timeouts appearing.

## Errors, timeouts, cancellations (QB-213)

`qwenbridge_api_errors_total{api,code,category,class}` counts every error response (v1, v2) and
failed stream (v2-sse).

| class | categories | Meaning |
|---|---|---|
| `expected` | VALIDATION, AUTHENTICATION, AUTHORIZATION, NOT_FOUND, CONFLICT, RATE_LIMIT, UNSUPPORTED, CANCELLED | The system rejected a request as designed |
| `operational` | TIMEOUT, DEPENDENCY, INTERNAL | Something failed |

- **Operational error rate** = operational errors ÷ all requests in the phase. Expected rejections
  are never counted as operational failures. Load phases that send deliberately invalid requests
  (`security-input`) report them as expected.
- **Interruptions:** `qwenbridge_api_interruptions_total{reason=CANCELLED|DEADLINE_EXCEEDED}`.
- **Per phase:** the performance report records deltas of all three counters (`server_errors`).

## Resources (QB-214)

`run-performance.py` samples during every phase, every 2 s:
- process and system CPU, heap used and max, GC pause count and seconds, and live threads (JVM
  binders at `/q/metrics`);
- the application container's CPU % and memory from `docker stats` (resident memory including
  native, not only heap).

Each phase reports average and maximum CPU, maximum heap, heap growth across the phase, GC pauses,
maximum threads and maximum container memory. The report's `environment` records:
- host CPU model and memory, OS;
- Docker version, CPUs and memory;
- the application container's limits, its Java version and options (`JAVA_TOOL_OPTIONS`);
- the Quarkus version.

## Redis (QB-215)

- **Redis running:** each phase records the deltas of `total_commands_processed`, `keyspace_hits`
  and `keyspace_misses` (Redis `INFO stats`).
  - **Amplification:** commands ÷ completed requests (`commands_per_request`). This is an
    approximation, because it counts every client, health checks included.
  - **Cache:** application-level hits and misses come from k6 (`cache_hits`, `cache_misses`) and
    `qwenbridge_redis_cache_events_total`.
- **Redis not running** (memory platform store, cache disabled): the report says
  `"redis": "not applicable: Redis not running"`. Zeros are never reported as if a cache were
  present.

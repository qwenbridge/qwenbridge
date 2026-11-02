# Docker resource retest — 2026-10-31

Historical measurement before optimization. See the [subsequent successful cold-cache and million-request run](performance-optimization-2026-10-31.md).

**The cold-cache five-second p95 target is still not met.** Increased Docker Desktop
resources were verified: **8 CPUs, 24,899,309,568 bytes of memory (~23.2 GiB)**.
The application remains Quarkus on Java 25. Product code and benchmark thresholds
were unchanged during this retest.

The prior 6.42-second result used native Mac Ollama for AI and Docker for the
application, Redis and OpenSearch. Both model placements were measured separately
this time; they must not be treated as the same hardware/runtime configuration.

| Model placement | Cold cache: 10 queries, 1 VU | Warm cache: 10 queries, 1 VU | Warm cache: 1,000 requests, 10 VUs |
| --- | --- | --- | --- |
| Native Mac Ollama, previous run | p95 **6,421.57 ms**, failed | p95 **76.50 ms**, passed | Not run in that comparison |
| Native Mac Ollama, current Docker resources | p95 **6,549.69 ms**, failed | p95 **87.53 ms**, passed | p95 **83.47 ms**, passed |
| Ollama inside Docker, current resources | p95 **13,278.78 ms**, failed | p95 **814.67 ms**, passed | p95 **355.42 ms**, passed |

Every measured phase had **zero HTTP errors and zero failed response-contract
checks**. Each cold phase had exactly **10 cache misses / zero hits**; each warm
phase had zero misses. Both 1,000-request phases completed all requests and all
**3,000 response-contract checks**. These are status/request-ID/pipeline-trace
checks, not a new semantic-relevance evaluation. Confidence telemetry did not
report unavailable analysis in these phases.

## Observed bottleneck

- With the native model, cold AI analysis alone had p95 **6,307.70 ms**. HTTP requests ranged from 5,632.91 to 7,053.32 ms. The five-second target failed even though models were loaded before the run.
- With the Docker model, cold AI analysis had p95 **12,565.95 ms**. `ollama ps` reported **100% CPU** model placement; the container consumed about **783% CPU** (~7.8 cores) during inference.
- Ollama used about **4.8 GiB out of its 8 GiB container limit** at the captured sample. Application memory remained below its 1 GiB limit. No OOM kills or container restarts were observed.
- Desktop memory allocation does not change Compose service limits: Ollama still has 8 GiB, the application 1 GiB and OpenSearch 1 GiB. OpenSearch was close to its configured limit, but the observed cold-request time was predominantly AI analysis.
- Current Docker Ollama configuration is `OLLAMA_KEEP_ALIVE=0`, `OLLAMA_MAX_LOADED_MODELS=1`, `OLLAMA_NUM_PARALLEL=1`. Native Ollama kept Qwen and BGE resident. These configuration/runtime differences are part of the separate all-Docker result; the table does not attribute the difference solely to Docker itself.

Increasing Docker memory alone has not brought the existing cold-inference path
under five seconds. These ten-query samples establish the current threshold result;
they do not establish a statistically significant performance change from 6.42 to
6.55 seconds. The cached 1,000-request workloads passed. A million-request run was
not executed and no million-request capacity claim is made.

## Method and evidence

The existing `scripts/performance/v9-k6-load-test.js` corpus and thresholds were
retained: p95 < 5,000 ms, p99 < 15,000 ms, zero failed checks and HTTP error rate
below 5%. An ignored copy of that script added cache-hit/miss and per-stage timing
telemetry. Queries cycle deterministically through all ten languages. Both AI
servers used the same Qwen 2.5 and BGE-M3 model digests; native Ollama was version
0.6.6 and Docker Ollama was version 0.40.13.

A fresh, task-specific cache prefix was used for each model placement. Existing
OpenSearch demo data was read without deleting or reseeding any index. No global
Redis flush or SLO adjustment was used. A single-request Docker readiness probe
was excluded from the full cold/warm comparison.

Machine-readable results: [resource-retest-2026-10-31.json](resource-retest-2026-10-31.json).
Raw k6 summaries, per-request samples, model placement and resource snapshots are
saved locally under `logs/verification/resource-retest-20261031-000508/`.

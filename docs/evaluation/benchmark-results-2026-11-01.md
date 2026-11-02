# Benchmark results, 2026-11-01 (QB-216 – QB-222)

**Status:** first full run of the 1.0 benchmark suite. Every number below was measured in this
run. Extrapolations appear only in [capacity-scenarios.md](capacity-scenarios.md), labelled as
such.

**Raw data:**
- `evaluation/benchmarks/2026-11-01/full-report.json` and `.md`: run `bench-full-20261101-202339`,
  69 minutes;
- `redis-report.json` and `.md`: run `bench-redis-20261101-213417`.

**Reproduce:** `BENCH_PROFILE=full BENCH_INCLUDE_JAVA=true python3 scripts/verification/run-benchmarks.py`,
with the Docker stack running. `BENCH_ONLY=<groups>` reruns a part.

## Environment

**One machine, not a production-like environment.**

| | |
|---|---|
| Host | Apple M4 Pro, 48 GB, macOS 26.6 |
| Docker | 28.5.1, 8 CPUs, 23 GB |
| Application | One container with a 1 GiB memory limit, `-XX:MaxRAMPercentage=55` (heap ≈ 545 MB), Java 25, Quarkus 3.40.1 |
| Model server | Ollama: `qwen2.5` 7.6B Q4_K_M (chat) and `bge-m3` 567M F16 (embeddings, 1024 dimensions). `OLLAMA_NUM_PARALLEL=1`, `OLLAMA_MAX_LOADED_MODELS=2`, 12 GB |
| Search, cache | OpenSearch (single node, small demo index), Redis 7.2 |
| Load generator | k6 1.7.1 on the same host, a closed model (constant VUs) or an open model (constant arrival rate) |

**Rate limits.** The application's per-IP limit was raised for the run, as the master performance
verification does, and restored afterwards.

## Retrieval engine, in process (QB-216 – QB-218)

`RetrievalBenchmarkTest` runs on synthetic corpora with precomputed query vectors, so there is no
embedding cost. It ran on 14 cores.

| Workload | Index | p95, 1 thread | QPS, 1 thread | QPS, 8 threads |
|---|---|---|---|---|
| Keyword BM25 | 1 k docs | 0.24 ms | 7,750 | 27,580 |
| Keyword BM25 | 10 k docs | 2.5 ms | 647 | 5,270 |
| Keyword BM25 | 100 k docs | 33.5 ms | 46 | 375 |
| Vector, 384 dims | 10 k | 4.5 ms | 321 | 2,440 |
| Vector, 384 dims | 50 k | 20.5 ms | 50 | 399 |
| Vector, 1024 dims | 10 k | 9.2 ms | 127 | 1,040 |
| Hybrid (RRF over both) | 10 k / 384 | 4.2 ms | 257 | 1,805 |
| RRF fusion alone | 2 × 30 candidates | 0.023 ms | 49,788 | — |

- **Scaling with size.** BM25 and exact cosine kNN grow linearly with the index; a tenfold index is
  about ten times slower.
- **Dimensions and top-K.** 1024 dimensions cost about 2.5× more than 384. Top-K (5, 20, 100) makes
  no measurable difference.
- **Fusion** is negligible next to retrieval: 0.02 ms of a 4.2 ms hybrid query.
- **Concurrency.** At 32 threads, p95 rises sharply; 10 k keyword goes from 2.7 to 14.5 ms because
  of lock contention on the index and CPU scheduling.
- **Implication.** The in-memory index suits up to about tens of thousands of documents per tenant.

## Search API, end to end (QB-216 – QB-218)

**Workload.** Warm analysis cache; "retrieval pool": English queries that need no translation;
OpenSearch backend; 20 s per level.

| Strategy | VUs | Achieved RPS | p50 | p95 | Errors |
|---|---|---|---|---|---|
| Keyword | 5 | 650 | 6.9 ms | 11.5 ms | 0 |
| Keyword | 20 | 1,590 | 12.1 ms | 18.0 ms | 0 |
| Keyword | 50 | 1,583 | 30.6 ms | 50.4 ms | 0 |
| Vector | 5 / 20 / 50 | 51 / 53 / 55 | 89 / 359 / 888 ms | 140 / 452 / 992 ms | 0 |
| Hybrid | 5 / 20 / 50 | 54 / 52 / 54 | 87 / 360 / 913 ms | 132 / 498 / 1,017 ms | 0 |

**Vector and hybrid are bound by query embedding, at about 55 requests/s.**
- **Cause.** Every vector or hybrid request embeds its query on the single Ollama slot, and server
  stage timing attributes the time to the `embedding` stage.
- **Effect.** Adding VUs only adds queueing: latency grows linearly while throughput stays flat.
- **Same limit as the direct measurement:** 55–65 embeddings/s, at any batch size or concurrency
  (`AiTransformationBenchmarkTest`).

## AI and language stages (QB-219)

| Stage | Provider | Concurrency | p50 | p95 | Throughput |
|---|---|---|---|---|---|
| Language detection | Lingua, in process | 1 / 8 | 0.02 ms | 0.4 / 1.0 ms | 3,223 / 24,312 per s |
| Intent analysis | Core rules | 1 / 8 | 0.1 ms | 0.2 ms | 11,467 / 58,892 per s |
| Constraint extraction | Core rules | 1 / 8 | 0.1 ms | 0.3 / 0.6 ms | 6,574 / 31,135 per s |
| Model analysis | qwen2.5 | 1 / 2 / 4 | 2.8 / 6.2 / 10.0 s | 3.8 / 7.6 / 12.6 s | 0.3 / 0.3 / 0.4 per s; at 4, 9 of 12 hit the 10 s analysis timeout |
| Commerce translation | qwen2.5 | 1 / 2 | 0.6 / 1.2 s | 4.7 / 1.7 s | 0.8 / 1.4 per s |
| Embedding, warm | bge-m3 | 1 (batch 1 / 8 / 32), 4 (batch 1) | 16.5 / 131 / 568 / 55 ms | 26 / 152 / 691 / 81 ms | 56 / 60 / 55 / 66 texts per s |

- **Deterministic stages** take microseconds.
- **Every model call is a multi-second, single-slot operation.** Translation configuration differs
  by pipeline:
  - **v1 pipeline:** `qwenbridge.translation.provider=none`.
  - **Commerce v2:** always translates non-English queries with the model, uncached.
- **No external providers** were configured, so none were measured.

## Combined workload (QB-220)

**Workload.** 20 requests/s in a fixed mix of the 31-query multilingual corpus: keyword 40 %, vector
12 %, hybrid 12 %, planned 10 %, suggestions 8 %, memory 8 %, v1 analyze 6 %, sessions 4 %.

**Result.** The system did not sustain it:
- **Served:** 4.6 requests/s at p50 42 s;
- **Timeouts:** 145 client timeouts (60 s);
- **Dropped:** 736 iterations could not start.

**The bottleneck isolated tests do not show.** Every component uses the single model slot:
- per-request translation of non-English queries;
- vector and hybrid query embeddings;
- uncached analyses.

Model work backs up, and requests that never touch the model wait behind it in shared worker and
connection pools. The multilingual keyword phase alone managed only 0.3 requests/s (p50 7 s at 2
VUs).

**Abandoned work.** Requests abandoned by client or deadline timeouts keep running inside Ollama.
After the combined phase, the model needed **150 s** before a 15 ms embedding answered again; a
separate case measured 110 s for 9 abandoned analyses.

## Spike, soak, stress (QB-221)

| Phase | Offered | Achieved | p50 | p95 | Errors | Notes |
|---|---|---|---|---|---|---|
| Baseline | 20/s | 20.0 | 10.6 ms | 13.5 ms | 0 | |
| Spike | 300/s | 300.0 | 5.2 ms | 7.1 ms | 0 | absorbed without queueing |
| Recovery | 20/s | 20.0 | 10.4 ms | 13.5 ms | 0 | identical to the baseline |
| Soak, 10 min | 50/s | 49.9 | 7.7 ms | 10.0 ms | 0 | 52 of 30,001 iterations dropped (0.17 %); heap peaked at 210 MB; container memory rose 504 → 610 MB over the earlier phases and the soak |
| Stress | 100–1,200/s | as offered | 5–8 ms | 7.6 → 19.1 ms | 0 | |
| Stress | 1,600/s | **730** | 541 ms | 4,486 ms | 0 | **saturated**: 14,617 dropped iterations; heap 416 MB, container memory 965 MB of 1,024 MB |

- **Keyword capacity on this machine:** about 1,200 requests/s sustained with p95 under 20 ms. It
  saturates between 1,200 and 1,600 requests/s.
- **Failure behaviour.** At saturation the server neither errored nor lost requests; it queued.
  Latency rose to seconds and the load generator could not start new iterations.
- **Memory after saturation.** Container memory stayed at about 980 MB for the rest of the run, so
  memory is not returned to the system quickly after a burst. Close to the 1 GiB limit, a longer
  saturation risks an out-of-memory kill (`ExitOnOutOfMemoryError` is set).

## Dependency outages (QB-222)

Each outage: 25 s before, 25 s with the container stopped, 25 s after it was started again, at 5
requests/s per workload.

| Outage | Workload during | Result | Recovery |
|---|---|---|---|
| Ollama | hybrid | 125 × typed 503 (operational) | ok |
| Ollama | vector | 126 × `REVIEW` (model safety decision unavailable) | ok |
| Ollama | keyword on the in-process memory backend | 126 × `REVIEW` | ok |
| OpenSearch | keyword on OpenSearch | 126 × typed 503 | immediate |
| OpenSearch | keyword on the memory backend | 125 × OK, unaffected | — |
| Redis | Commerce keyword | 126 × typed 503 | immediate |
| Redis | v1 analyze | 100 × OK, p50 6.9 s (cache down; every call reaches the model), 26 dropped | immediate |

- **Correct behaviour:**
  - every failure is a typed response, with no untyped 5xx and no transport errors;
  - nothing returned fabricated semantic results, because without the embedding model vector and
    hybrid requests fail;
  - an invalid credential was rejected with 401 in every window, so authorization never degraded;
  - a dead OpenSearch does not affect the in-process backend.
- **Findings:**
  1. **Commerce search fails entirely without the model**, even on the in-process backend, because
     the model's safety decision is mandatory (fail closed, by design). Without an AI provider,
     Commerce search is unavailable.
  2. **Inconsistent outage responses.** During the Ollama outage, hybrid requests answer 503 while
     vector requests answer `REVIEW`.
  3. **Redis is required for Commerce v2 in this Docker configuration** (Redis evidence and
     analytics store). With `qwenbridge.platform.store=memory` it is not (ADR 0043).
  4. **The "before" windows of the Ollama outage were slow** (p50 22–25 s). They followed the
     combined phase, so the model backlog had not drained (drain measured 65–70 s).

## Redis on, off, unavailable (QB-215)

**Workload.** v1 analyze, 20 distinct queries, 1 VU (`redis-report.json`).

| Configuration | p50 | p95 | Errors | Redis commands per request |
|---|---|---|---|---|
| Redis on, warm cache | 10.1 ms | 15.6 ms | 0 | 3.05 (20 hits) |
| Redis on, filling | 4.0 s | 4.4 s | 0 | 4.95 (20 misses) |
| Redis off (cache disabled, memory platform store) | 4.1 s | 4.4 s | 0 | not applicable: the standalone server used no Redis; the 0.8 commands per request seen were the Docker app's health checks |
| Redis unavailable (cache enabled, unreachable) | 4.2 s | 4.8 s | 0 | not applicable |

- **The cache is the whole difference:** about 400× on repeated queries.
- **Without Redis the API keeps working;** every analysis reaches the model.
- **Earlier Commerce amplification.** The batch 30a measurement of about 23 Redis commands per
  Commerce search stands; it covers evidence, analytics and the analysis cache.

## Recommendations (not implemented in 1.0)

1. **Cache Commerce translations** by text and language pair, and use the model's language
   detection only when Lingua is unsure. Lingua classified "lightweight laptop under 1500 EUR" as
   Danish, which triggered a model translation.
2. **Separate the model servers.** Run embeddings on a model server with parallel slots (or a
   separate instance), so chat work cannot starve query embedding.
3. **Cancel model requests whose client has gone.** This means streaming with cancellation, or a
   per-request deadline on the model call, so abandoned work stops consuming the slot.
4. **Raise the container memory limit,** or tune heap and direct memory, before running near
   1,200 requests/s.
5. **Add a similarity threshold** to vector retrieval (quality evaluation, QB-210).

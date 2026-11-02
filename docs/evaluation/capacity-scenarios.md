# One-million-user scenarios and actual capacity (QB-223)

"One million users" names four different requirements. Each is modelled below, then compared with
**measured** single-node capacity ([benchmark-results-2026-11-01.md](benchmark-results-2026-11-01.md)).

**Labels.**
- *Measured:* observed in a run.
- *Assumed:* a modelling input, not a fact about any deployment.
- *Extrapolated:* arithmetic on measured numbers; not verified.

None of these scenarios has been verified at scale; there is no production-like environment (audit
H7).

**Measured capacity used below** (one machine, one 1 GiB app container, one Ollama with one slot):

| Path | Measured sustainable rate |
|---|---|
| Keyword search, analysis cached, English | ≈ 1,200 req/s (p95 19 ms); saturates between 1,200 and 1,600 |
| Vector or hybrid search, analysis cached | ≈ 55 req/s (query embedding on one model slot) |
| Any request needing model analysis (cache miss) | ≈ 0.3–0.4 req/s per model slot (p50 2.8 s) |
| Non-English Commerce query (model translation) | ≈ 0.8–1.4 req/s per model slot |
| Cached v1 analyze, 1 M requests at 250 VUs (Section 2 gate) | p95 118 ms, 0 errors (measured 2026-11-01) |

## 1. One million registered users

| | |
|---|---|
| What it means | Accounts and tenant state exist; most users are idle |
| Request frequency | Not a throughput requirement on its own |
| Data size | *Assumed:* per user, an API principal plus sessions bounded by `qwenbridge.platform.max-value-bytes` (1 MB) and the session TTL |
| Topology | Platform state in Redis (or the bounded memory store for one node) |
| Verdict | **Not measured.** QwenBridge has no user store; identities are operator-configured API principals. Storage at this scale depends on the Redis deployment, which was not tested at this size |

## 2. One million daily active users

| | |
|---|---|
| Request frequency | *Assumed:* 10 searches per user per day, so 10 M requests/day |
| Average / peak | *Extrapolated:* 116 req/s on average; with an *assumed* peak-to-average ratio of 3, 350 req/s |
| Workload mix | *Assumed:* 60 % keyword, 25 % vector or hybrid, 15 % needing new model work (new or non-English queries) |
| Latency objective | *Assumed:* p95 < 500 ms for retrieval, < 5 s when the model is involved |
| Data size | *Assumed:* 100 k products per tenant |

**Against measured capacity (extrapolated, not verified):**
- **Keyword:** 210 req/s at peak fits one node (≈ 1,200 measured).
- **Vector or hybrid:** 88 req/s at peak exceeds one model slot (55 measured), so about 2 embedding
  slots or instances are needed.
- **Model work:** 53 req/s at peak at 0.35 req/s per slot needs about 150 model slots. This
  dominates the cost and is far beyond anything measured here.
- **Index:** 100 k products in the in-memory index measured BM25 p95 34 ms single-threaded; use a
  search backend (the optional OpenSearch backend) at that size.

**Verdict.** Not supported by measured evidence. The retrieval tier is plausible; the model tier
would need a large, separately designed inference fleet and caching.

## 3. One million requests per day

| | |
|---|---|
| Average / peak | *Extrapolated:* 11.6 req/s on average; peak 35 req/s at an *assumed* ratio of 3 |
| Mix | as in scenario 2 |
| Latency objective | as in scenario 2 |

**Against measured capacity:**
- **Retrieval:** 35 req/s of cached keyword traffic (*measured* 1,200) or 9 req/s of vector traffic
  (*measured* 55) fits one node.
- **Volume:** one million cached requests were *measured* in one run (Section 2 gate: 1 M requests,
  250 VUs, p95 118 ms, 0 errors).
- **Model work:** 15 % of the peak is 5 req/s, which needs about 15 model slots (*extrapolated*).
  One slot sustains about 30,000 analyses per day; with a high analysis-cache hit rate, the slot
  requirement falls proportionally.

**Verdict.** Supported for cached, English retrieval traffic on one node (measured). Not supported
on one model slot when more than about 3 % of requests need new model work or translation.

## 4. One million simultaneous concurrent users

| | |
|---|---|
| Request frequency | *Assumed:* one request per user every 30 s, so 33,000 req/s sustained |
| Concurrency | 1 M open sessions, or SSE streams, held at once |
| Topology | Many app nodes behind a load balancer, a shared Redis cluster, a search cluster, and an inference fleet |

**Against measured capacity (extrapolated):**
- **App nodes:** 33,000 ÷ 1,200 ≈ 28 app nodes for cached keyword traffic alone, assuming linear
  scaling. Linear scaling was *not* tested; only one node was measured.
- **Streams:** 1 M concurrent SSE streams were never tested. The per-node stream capacity is
  limited to 25 streams per endpoint by design.

**Verdict.** **Not supported, and not claimed.** It is outside every measured configuration by
orders of magnitude.

## What would be needed to verify these

- A production-like environment: several nodes, a separate Redis, a search cluster, and an
  inference server with parallel slots.
- Load generation from separate machines.
- A realistic query distribution with a measured cache hit rate.
- A soak of hours, not minutes.

The suite (`run-benchmarks.py`) and the regression gates are ready to run there unchanged.

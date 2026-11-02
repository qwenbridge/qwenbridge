# Docker performance optimization — 2026-10-31

> **Historical record.** It describes the repository at the time it was written. Modules and connectors it mentions may have been removed since: QB-287 removed the Elasticsearch, PostgreSQL, MongoDB and Shopify connector modules and `examples/` (ADR 0058). For the current state, start at [docs/README.md](../README.md).

**The cold-cache latency target and the complete million-request workload now pass.**
The server remains Quarkus 3.40.1 on Java 25. Model weights are unchanged:
Qwen2.5 `845dbda0ea48…` and BGE-M3 `790764642607…`. All services, including
Ollama, ran inside Docker Desktop with 8 CPUs and 24,899,309,568 bytes VM memory.

## Measured results

Final run: `20261031-010519`, Dockerized k6, fresh demo index and isolated Redis
prefix. Thresholds remain p95 < 5,000 ms, p99 < 15,000 ms, all response checks
passing, and every requested iteration and HTTP call completed. Redis pool
rejections and analysis-cache fallbacks must also remain zero.

| Phase | Completed requests | Concurrent VUs | p95 | p99 | Cache hits / misses |
| --- | ---: | ---: | ---: | ---: | ---: |
| Cold analysis cache | 10 | 1 | **2,791.42 ms** | 2,890.76 ms | 0 / 10 |
| Warm stage | 1,000 | 10 | 17.74 ms | 26.69 ms | 1,000 / 0 |
| Warm stage | 10,000 | 50 | 23.36 ms | 34.29 ms | 10,000 / 0 |
| Concurrency gate | 10,000 | 250 | 65.23 ms | 81.62 ms | 10,000 / 0 |
| Million-request workload | **1,000,000** | **250** | **43.09 ms** | **51.14 ms** | **1,000,000 / 0** |

Every phase passed with zero HTTP errors, failed response checks, Redis pool
rejections and cache fallbacks. The million-request phase completed **6,000,000
response checks**, took **164.94 seconds**, and averaged **6,062.85 requests/s**.
The previous Docker cold-cache p95 was 13,278.78 ms; see the preserved
[resource-only retest](resource-retest-2026-10-31.md).

The cold phase measures ten distinct queries with resident model weights.
Throughput phases repeat those ten multilingual queries against five demo
products. These results establish cached capacity for this workload. They do
not measure a million unique queries, simultaneous users, or uncached model
capacity at 250 VUs. Cold-cache coverage is a ten-query smoke measurement,
not a statistically broad inference benchmark. Model startup/download and
health checks run separately before these phases.
The load checks require available search execution and valid model analysis;
they do not assert result relevance for each load query. The separate retrieval
script checks the five expected product titles.

## Causes and changes

- Ollama previously unloaded model weights between calls. Compose now permits
  two resident models, keeps them for ten minutes, and sets configurable 12 GB
  Ollama / 2 GB OpenSearch memory limits.
- Search analysis emitted many redundant JSON fields. An internal versioned
  schema reduces generated output to approximately 80 tokens in the initial
  experiments, while the existing REST/SSE analysis shape stays the same.
  Temperature is zero for schema-constrained analysis. Ordinary chat keeps its
  previous sampling/output behavior. The parser strictly validates the compact
  format and retains legacy-format support. Analysis cache version is now `v5`.
- One real-model quality test caught untranslated Persian in the rewrite array.
  The prompt was corrected before load testing. Live tests now verify English
  negation/budget preservation, Persian amount/currency translation, and comparison
  intent with both product names retained.
- OpenSearch responses previously included 1,024-dimensional product embeddings.
  These are excluded from returned documents by default, while backend vector
  retrieval remains available. An explicit setting restores embedding output.
- At 250 VUs, Redis acquisition queue overflow caused false cache misses and
  unnecessary model calls. Production metrics showed 1,075 pool rejections.
  A debugger logpoint captured `ConnectionPoolTooBusyException: Connection pool
  reached max wait queue size of 24`; the concurrent reproducer observed 846
  false misses among 1,000 reads. The pool now has 32 connections and a bounded
  queue of 1,024. The live regression passes four waves of 250 concurrent reads
  without a false miss.
- The verifier now separates cold and cached phases, checks actual request counts,
  detects degraded/fallback analysis, and gates the million run with a smaller
  check at the requested concurrency. It preserves failures and restores ordinary
  application rate limits on exit. The SSE verifier also clears its timeout timer
  after completion instead of leaving Node idle for 65 seconds.

The first million-request attempt was deliberately stopped after the Redis issue
was observed: 2,368 requests completed, p95 27,533.59 ms, and the run correctly
failed. That evidence is retained under run `20261031-005520`; it was not counted
as a successful million-request test.

Redis pool settings follow the [Quarkus 3.40 Redis configuration reference](https://quarkus.io/version/3.40/guides/redis-reference/).
Schema-constrained generation uses the [Ollama chat API format/options](https://github.com/ollama/ollama/blob/main/docs/api.md).

## Verification

| Check | Result |
| --- | --- |
| Complete Java 25 Maven reactor, all live integration flags enabled | **621 passed: 583 server + 38 SDK; zero failures, errors or skips** |
| TypeScript SDK | **33 tests / 6 files passed**, build, pack and example build passed |
| Maven `code-quality` profile | Spotless and SpotBugs passed across the reactor |
| Bash verifier regressions | Passed, including incomplete-load rejection and Bash 3.2/5 syntax |
| Final live release verifier | **70 passed, zero failed**, one explicit local-suite skip because it ran separately |
| Release warnings | Existing dirty worktree and absent strict release tag; neither indicates a runtime failure |
| Additional k6 security script | 9 requests, 18 checks, zero failures |
| Additional k6 language script | 11 requests, 22 checks, zero failures |
| Additional k6 retrieval script | 5 requests, 15 checks, zero failures |
| Additional k6 resilience script | 4 requests, 8 checks, zero failures; expected validation statuses accepted |

Logs and phase JSON summaries are in
`logs/verification/performance-20261031-010519/`; the release log is
`logs/verification/verify-release-20261031-010519.log`. The adjacent
[machine-readable report](performance-optimization-2026-10-31.json) preserves
the exact metrics and test counts. These logs are intentionally ignored by Git.

During the corrected load, the app used about 595 MiB of its 1 GiB limit; Ollama
used about 5.99 GiB and was idle in the cached phase. There were no app restarts
or OOM kills. Afterward, readiness reported Ollama/OpenSearch/Redis all UP, and
the app had normal limits again: 60 requests/IP/window and 120 AI requests/window.
The verified stack is left running on `http://127.0.0.1:18080`.

## Repeat the flow

With Docker Desktop running, from the repository root:

```bash
bash scripts/verify-performance.sh
```

Or, from `scripts/`, use `bash verify-performance.sh`. The entry point prepares
the stack and runs local verification by default. For the exact ports used here,
after the local suite has already passed:

```bash
APP_PORT=18080 OLLAMA_PORT=11435 OLLAMA_URL=http://127.0.0.1:11435 \
RUN_LOCAL_VERIFICATION=false bash scripts/verify-performance.sh
```

See [testing instructions](../development/testing.md#performance-checks) for a
smaller run and configuration overrides. No Shopify client/account is involved
in this OpenSearch demo workload. Product-phase status remains the separate
[50-phase evidence matrix](phase-acceptance.md); these performance results do
not complete missing commerce, connector, pack or Machine-to-Human features.

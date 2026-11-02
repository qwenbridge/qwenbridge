# ADR 0045: Benchmark Suite, Capacity Model and Regression Gates

## Status

Accepted (QwenBridge 1.0, Section 3 Phase 30, part 2: QB-216 – QB-224)

## Context

Performance evidence was one k6 workload (cached analysis) and a throughput run of a single
English query. There were:
- no vector, hybrid, AI, combined, spike, soak, stress or outage measurements;
- no capacity model;
- no regression gate against recorded baselines.

## Decision

1. **Measured, reproducible benchmarks.**
   - **Core engine:** `RetrievalBenchmarkTest` covers index size, vector dimension, top-K,
     concurrency and fusion cost.
   - **AI and language stages:** `AiTransformationBenchmarkTest` records model identities, separates
     cold starts, and measures the model backlog drain.
   - **HTTP suite:** `run-benchmarks.py` and `k6-bench.js` cover:
     - strategy load at 3 concurrency levels;
     - multilingual, combined and cold workloads;
     - spike, soak and stress;
     - outages of Ollama, OpenSearch and Redis (stop and start only, always restarted);
     - Redis on, off and unavailable.
   - **Per phase, the suite records:** achieved RPS separate from offered load, latency
     percentiles, typed outcomes, expected and operational errors, server stage histograms,
     resources, Redis deltas, the model backlog drain, and the environment.
2. **Isolation between phases.**
   - **Backlog drain:** before each phase the model backlog is drained.
   - **Separate pools:** retrieval phases use queries measured to need no translation;
     model-bound traffic is measured separately and then mixed.
   - **Rate limits:** the application's per-IP limit is raised for the run and restored, as the
     master performance verification does.
3. **Results as versioned evidence.** `evaluation/benchmarks/2026-11-01/` holds the first full run
   and the Redis comparison. The findings are recorded in
   `docs/evaluation/benchmark-results-2026-11-01.md`.
4. **Capacity model.** `docs/evaluation/capacity-scenarios.md` models the four one-million-user
   scenarios, labelling measured, assumed and extrapolated values. No scale is claimed beyond
   measurement.
5. **Regression gates.**
   - **Approved baselines:** `evaluation/baselines/baseline.json` holds real values with their
     sources (quality, Core retrieval, master performance phases, and the benchmark suite).
   - **Gate script:** `regression_gate.py` fails on a regression beyond tolerance, and on a missing
     measurement (never a silent pass). It reports other workloads as "not gated".
   - **Master steps:** the gate runs against the current run, and a Scenario J step proves the gate
     detects a real 5 ms-per-call slowdown injected into the Core benchmark.
   - **Hardware-bound gates (2026-11-02):** latency and throughput gates are marked
     `hardware_bound` and apply only on the hardware the baseline names (`hardware`). A run on other
     hardware sets `QWENBRIDGE_PERF_HARDWARE`; its latency gates are reported as "not gated" with
     the measured value, while quality and error gates still apply. The Master Verification
     workflow on GitHub's 4-CPU runners does this. There, Scenario J injects 50 ms
     (`QWENBRIDGE_BENCHMARK_INJECTED_DELAY_MS`) and compares the slowed run with the same run
     (`--rebase`), and the cold k6 phase uses CPU-model latency limits (`PERF_COLD_P95_MS`,
     `PERF_COLD_P99_MS`).

## Consequences

- **Bottlenecks revealed.** The suite showed the single model slot is the bottleneck for every
  vector, hybrid, multilingual and uncached path. Abandoned model work blocks later requests for up
  to 150 s. Keyword retrieval saturates between 1,200 and 1,600 requests/s on one 1 GiB container.
  These are documented with recommendations, not fixed in 1.0.
- **Laptop baselines.** Baselines come from one laptop. Tolerances are wide (+50 % to +100 %
  latency, 0.02 nDCG), and a production-like environment needs its own approved baselines.
- **Cost.** The full suite takes about 70 minutes and is not part of every master run. The master
  runs the quick Core benchmark, the quality evaluation and the gates.

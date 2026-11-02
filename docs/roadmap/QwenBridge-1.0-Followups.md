# QwenBridge 1.0: Follow-up Tasks

Work deliberately deferred while Sections 1–3 were completed, so that none of it is forgotten.
Nothing here blocks Section 4 or local development.

**Sources:**
- measurements in `docs/evaluation/benchmark-results-2026-11-01.md` (one laptop, one 1 GiB app
  container, one single-slot Ollama);
- the PARTIAL items in the Section 1–3 reports.

**Priority:**

| Priority | Meaning |
|---|---|
| **P1** | Fix in code before any production or pilot deployment; these occur on any hardware |
| **P2** | Configuration, deployment or an opt-in feature |
| **P3** | Needs data or an external prerequisite first |

Each item says how to verify it, so a fix comes with a measured before and after.

## A. Hardening from measurement (Section 3, Phase 30)

| ID | Priority | Problem (measured) | Proposed fix | Verify by |
|---|---|---|---|---|
| FU-01 | P1 | **Abandoned model work.** When a request times out or is cancelled, Ollama keeps working on it. With one model slot, later requests queue behind it. The backlog took **110–150 s** to drain after 9 abandoned analyses or the combined workload | When the request deadline or cancellation fires, close the HTTP call to the model server (Ollama stops generating when the client disconnects), instead of only abandoning the waiting future. This also completes the QB-182 PARTIAL | `AiTransformationBenchmarkTest`: `model-backlog-drain` should fall from about 110 s to near 0. Benchmark `combined` and `cold-keyword` drain times |
| FU-02 | P1 | **Translation on every request.** Commerce v2 translates every non-English query with the model, uncached, even with `qwenbridge.translation.provider=none` (that setting only applies to v1). The multilingual keyword pool managed **~0.3 req/s**, at a p50 of 7–17 s | Cache translations by (normalized text, source language, target language, model version) in the same store as the analysis cache, with a TTL | Benchmark `multilingual-keyword-*` throughput and the `translation` stage in server histograms |
| FU-03 | P1 | **Language misdetection triggers model calls.** Short English queries are sometimes misdetected; "lightweight laptop under 1500 EUR" was detected as Danish, which caused a 513 ms model translation | Skip translation when detection confidence is low and the text is Latin-script and short; keep the declared or catalog language | A language-detection test on the retrieval-pool queries; `translation` stage ≈ 0 for them |
| FU-04 | P2 | **Commerce fails entirely without the model.** It is unavailable while the model is down, even on the in-process backend (fail-closed safety, by design). During an outage, hybrid answers 503 while vector answers `REVIEW` | (a) Make the outage answer consistent across modes. (b) **Product decision needed:** an opt-in degraded mode (flag, off by default) where Core's deterministic threat rules and constraint extraction stand in for the model, with every response labelled degraded | The benchmark Ollama outage phases |
| FU-05 | P2 | **Model throughput.** One single-slot Ollama gives ~55 embeddings/s and ~0.3 analyses/s, which caps vector and hybrid search at ~55 req/s | Deployment guidance: `OLLAMA_NUM_PARALLEL`, a separate embedding server, GPU sizing. Optional: cache query embeddings | Benchmark `strategy-vector-*` and `strategy-hybrid-*` on the target environment |
| FU-06 | P2 | **Memory after a burst.** Keyword search saturated between 1,200 and 1,600 req/s. The container then sat at 965 MB of its 1,024 MB limit and did not give the memory back (no leak in the 10-minute soak) | Document container and heap sizing (explicit `-Xmx`, MaxDirectMemory, a larger limit); scale horizontally for capacity | A rerun of the benchmark `stress-*` and `soak` phases with the new settings |
| FU-07 | P2 | **Vector results without relevance.** Vector retrieval has no similarity threshold, so it always returns K results, even for queries with no answer (false-positive rate 1.0 on negative queries) | An optional per-request or per-index minimum similarity, reported as a typed outcome | `RetrievalQualityEvaluationTest`: the negative false-positive rate drops without losing nDCG |
| FU-08 | P3 | **Hybrid not best on the evaluation set.** It scored nDCG@5 0.935 against 0.984 for vector alone. The set is only 36 synthetic documents with a single annotator | Do **not** change defaults on this evidence. Re-evaluate fusion weights after FU-13 | The quality evaluation on the new dataset |
| FU-09 | P2 | **Redis needed for Commerce v2 in Docker.** It holds evidence and analytics; the memory store (`qwenbridge.platform.store=memory`) works only for single instances | Decide the production store for evidence and analytics (Redis cluster, or a platform-store adapter); document it | Benchmark Redis outage phases |
| FU-10 | P2 | **Redis amplification.** About 23–26 Redis commands per Commerce search, against 3 for cached analyze | Review the evidence and analytics writes (batching or pipelining) | `commands_per_request` in performance reports |

## B. Open PARTIAL items from Sections 1–3

| ID | Task | What is missing | Priority |
|---|---|---|---|
| FU-11 | QB-148, QB-053, QB-086 | Commerce search, interpretation and mapping code still live in the server; needs the pack vocabulary SPI | P2 (planned with Section 4 packs) |
| FU-12 | QB-160, QB-166 | The OpenSearch **Commerce** path uses the server's REST client and TLS registry, not the connector module | P2 |
| FU-13 | QB-207, QB-124, QB-028 | An independent, multi-annotator relevance and intent dataset (audit H7) | P3 |
| FU-14 | QB-184, QB-006 | Trace propagation through the v1 pipeline (the PostgreSQL part lapsed with the connector's removal in QB-287) | P2 |
| FU-15 | QB-182 | Request scope (deadline and cancel) for the v1 pipeline and AI token streaming; see FU-01 | P1 with FU-01 |
| FU-16 | QB-197 | An automated WCAG audit (axe) and browsers other than Chromium | P2 |
| FU-17 | QB-206 | Run the dual-SDK live Shopify check (`AUTHORIZED_LIVE_PROVIDER`) against a real development store (needs credentials) | P3 |
| FU-18 | QB-208 | A test where one sub-query's provider fails while the others succeed | P2 |
| FU-19 | QB-129, QB-136 | Validation of model rewrite suggestions; persisting negative feedback into session context | P2 |
| FU-20 | QB-013, QB-017, QB-018, QB-031 | Same-script language mixing; Commerce's own `Translation` record; strict operator checks beyond en, sv and fa; natural-language dates and cities | P2 |
| FU-21 | — | The generic 400 mapper says "Malformed JSON request body" for every 400 (pre-existing) | P2 |
| FU-22 | — | Open security findings S1 (CORS), S9, S10, S11; S8 and S13 now mitigated (see the Section 4 audit) | P1 before production |
| FU-23 | QB-281 | **S1b:** the production configuration validator checks the profile `production`, but Quarkus's production profile is `prod`, so the validator never runs | P1 before production |
| FU-24 | QB-281 | OWASP Dependency-Check (NVD) could not run offline; run it with a current NVD database and a failing threshold (S10) | P1 before release |
| FU-25 | QB-273 | No SDK licensing API (SDKs only see 403 entitlement errors) | P3 |

## C. Before a production or pilot deployment

1. **Fixes:** FU-01, FU-02, FU-03, FU-15, FU-22, FU-23 and FU-24.
2. **Decision:** FU-04 (degraded mode, yes or no) and FU-09 (production store).
3. **Measure on the target environment:**
   - run `scripts/verification/run-benchmarks.py` there;
   - approve new baselines in `evaluation/baselines/baseline.json`;
   - redo the capacity model in `docs/evaluation/capacity-scenarios.md` with those numbers.

# Verification results — 2026-10-30

> **Historical record.** It describes the repository at the time it was written. Modules and connectors it mentions may have been removed since: QB-287 removed the Elasticsearch, PostgreSQL, MongoDB and Shopify connector modules and `examples/` (ADR 0058). For the current state, start at [docs/README.md](../README.md).

Latest: [optimized Docker run, cold p95 passing and one million requests completed](performance-optimization-2026-10-31.md), including 621 passing Java tests. The original measurements below remain preserved.

The application, Java SDK, Java examples, Docker build/runtime and IntelliJ project
are configured for **Java 25**. The server is **Quarkus 3.40.1/CDI**. Java class
files in all three Maven modules were inspected and have major version 69.
Maven/npm/runtime versions now agree on **0.9.1**.

**The implementation is not all 50 product phases.** See the [phase-by-phase evidence
matrix](phase-acceptance.md): 6 implemented within the stated scope, 19 partial and
25 missing. No test result below implies delivery of absent capabilities.

## Final results

| Check | Observed result |
| --- | --- |
| Complete Maven reactor, `clean install`, live integrations enabled | **595 tests passed: 557 server + 38 SDK; 0 failures, 0 errors, 0 skipped** |
| Real Qwen chat/pipeline and BGE-M3/OpenSearch hybrid tests | All three formerly opt-in/skipped tests passed in the final complete reactor run |
| TypeScript SDK | **6 files / 33 tests passed**; build, package dry-run and example build passed |
| npm dependency audit | Passed, no reported vulnerabilities at execution time |
| Maven code-quality profile | Spotless and SpotBugs passed for the complete reactor; documented narrow exclusions are in `spotbugs-exclude.xml` |
| Bash/tool verification | Syntax and exit-code/skip/JDK/version-drift regressions passed on Bash 3.2 and 5.2; all k6 files passed JavaScript syntax and k6 inspection |
| Full release verifier, launched from `scripts/` | **69 passed / 0 failed / 2 explicit skips**: local suite was executed separately; the million-request workload was not run |
| Final rebuilt Docker image | Healthy; UID/GID **65532:65532**; runtime **Temurin 25.0.5**; Quarkus fast-jar starts correctly |
| Final SSE checks after cleanup fix | Lifecycle and real AI token streaming both passed; curl processes are terminated directly |
| k6 security / language / retrieval / resilience | All four scripts passed with deterministic smoke coverage, including all 11 language cases |
| k6 mixed-language **cold** cache, 10 requests / 1 VU | All **30 correctness checks passed**, zero HTTP errors; **failed** the unchanged p95 < 5,000 ms threshold: measured **6,421.57 ms** |
| Same deterministic mixed workload, immediately **warm** | All **30 correctness checks passed**, zero HTTP errors; all thresholds passed, **p95 76.50 ms** |

These k6 runs are smoke tests. They do not establish production capacity, a
million-request result or performance at 250 concurrent users. Cold inference on
the selected Qwen model still needs latency work if the 5-second p95 is required.
The failing cold result is retained; thresholds were not relaxed to make it green.
An earlier random selection did not cover every language in ten iterations, so
selection was made deterministic before the final cold/warm comparison.

## Reproduction

From the repository root:

```bash
bash scripts/verify-local.sh
bash scripts/verify-release.sh
```

From `scripts/`:

```bash
bash verify-local.sh
bash verify-release.sh
```

The scripts select an installed JDK 25. Direct Maven commands need a JDK 25 terminal:

```bash
QWENBRIDGE_RUN_OLLAMA_IT=true \
QWENBRIDGE_RUN_OPENSEARCH_IT=true \
QWENBRIDGE_SEARCH_OPENSEARCH_INDEX=<seeded-test-index> \
mvn -B clean install
mvn -B -Pcode-quality -DskipTests verify
```

For the recorded live Docker run, the application used port 18080, the Docker
Ollama endpoint used 11435, and the application connected to the existing native
Qwen/BGE server through `host.docker.internal:11434`. This allowed acceleration on
the Mac. Redis and OpenSearch ran in Docker. Both native and Docker model stores
contained the configured Qwen 2.5 and BGE-M3 models. The default all-Docker CPU model
path was not used to establish these latency numbers.

```bash
APP_PORT=18080 OLLAMA_PORT=11435 \
OLLAMA_URL=http://127.0.0.1:11435 \
QWENBRIDGE_AI_OLLAMA_BASE_URL=http://host.docker.internal:11434 \
RUN_LOCAL_VERIFICATION=false PULL_DOCKER_IMAGES=false \
bash scripts/verify-release.sh
```

Use the standard defaults when no native model server is running. The release
verifier preserves volumes and creates a fresh verification index/cache prefix.
The seed script now refuses existing-index replacement without an explicit reset.
An initial seed request against the default index was rejected by automatic review
because the old script deleted that index unconditionally. All audit retrieval tests
then used newly named indices, with no existing index deleted.

## Runtime defects confirmed and fixed

- **Abuse checks were bypassed by a path mismatch.** Quarkus supplied a path already beginning with `/`; the old filter prepended another slash and failed its `/api/` check. The debugger observed `raw=/api/v1/ai/chat`, request length **70013**, and enabled abuse protection. The corrected path remains `/api/v1/ai/chat`. Regression tests now return 413 before AI invocation for both known-length and chunked bodies, and verify rate-limit headers on ordinary API requests.
- **SSE headers were added too late.** The first event could commit the response before the JAX-RS response filter. Common headers are now populated before that event. The debugger observed `headWritten=false` when early headers were set; live SSE and API header regressions passed.
- **An empty model rewrite list escaped unchanged.** The live model exposed this intermittently. A deterministic debugger reproduction observed `rewriteCount=0` and `RewriteResult[performed=false, provider=qwen-analysis, rewrites=[]]` for `mouse under 50 EUR`. The step now returns the original query and identifies the fallback, without marking it as an AI rewrite. The new regression and complete live suite passed.
- **Bash verification could misreport success.** The historical wrapper discarded failures, starter checks required deleted modules, and some Docker failures were followed by successful diagnostic commands. The entry points now preserve nonzero status, separate skips and stop dependent tests on prerequisite failure.
- **Docker did not package Quarkus correctly.** It used Java 21, copied deleted starter POMs and treated a thin JAR as executable. It now copies the complete `quarkus-app` directory and runs `quarkus-run.jar` on Java 25. This follows the [Quarkus fast-jar packaging contract](https://quarkus.io/version/3.40/guides/packaging/).
- **Quality/performance checks had gaps.** k6 files contained invalid escaped semicolons and lacked assertion thresholds; the formatter was too old for JDK 25. Real mutable collection ownership issues were fixed, rather than disabling the quality profile.

IntelliJ initially refused debugger startup because only JDK 21 was registered.
The installed JDK 25 was registered and selected for this project; the IDE build
then passed. All debugger probes created by this audit were removed, and no debug
session was left running. Existing user breakpoints were preserved.

## Evidence files

Machine-readable results: [verification-results.json](verification-results.json).
Local raw logs are under `logs/verification/audit-20261030/`, and k6 summaries are
under `logs/verification/`. These generated logs remain ignored by Git.
Pre-existing worktree changes were preserved. The repository's configured formatter
was applied because the Java 25 quality gate otherwise failed on the migrated code.

No Shopify account, external store or production system was contacted. No license,
marketplace, commerce connector or Machine-to-Human feature is claimed complete
without implementation and acceptance evidence.

After verification, the task-owned Docker/native test services were stopped. Downloaded
models, named volumes, isolated audit indices and local logs were preserved.

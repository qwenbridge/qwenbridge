# Testing

The server uses Quarkus/CDI and all Java modules use JDK 25. The Java SDK, public
plugin SDK and examples inherit release 25; the SDKs do not depend on Quarkus.
There is no Spring Boot server or starter in the reactor.

### Dual-SDK end-to-end suite (QB-295)

The Java and TypeScript SDKs run as independent consumers of the real packaged server, with
shared scenarios and one evaluator: offline baseline, local provider integration and authorized
live providers. Master step 24 and the CI job `dual-sdk-e2e` run it. See
[e2e/dual-sdk/README.md](../../e2e/dual-sdk/README.md).

### Quarkus-only guards (QB-294)

- **Every build:** `QuarkusOnlyArchitectureTest` (server tests) checks every reactor module's sources,
  resources and POMs, plus the Dockerfile, Compose files and `.env.example`. It looks for
  dependencies, annotations, startup code, test support, configuration keys, environment variables
  and executable-jar packaging of another application framework.
- **Master verification:** step 23 (`scripts/verification/23-quarkus-only.sh`) additionally checks:
  - Java 25 for the JVM and Maven;
  - every module's resolved dependency tree and every build plugin with its dependencies;
  - the repository audit (`scripts/verification/quarkus_only_audit.py`);
  - the packaged fast-jar;
  - the running Compose container.
- **Self-test:** `scripts/tests/verification-test.sh` proves the audit rejects each kind of
  violation.

## Local checks

From any working directory, run the entry point by its path:

```bash
bash scripts/verify-local.sh
# From scripts/: bash verify-local.sh
```

The verifier selects an installed JDK 25 (current Java, macOS java_home, or SDKMAN).
Set `QWENBRIDGE_JAVA_HOME=/path/to/jdk25` to choose explicitly. A missing or wrong
explicit JDK is an error. Maven also enforces JDK 25 and Maven 3.9+.
Node 22.12+, 24 or 26 is required for the current Vitest development tools.

This runs version consistency, verification-tool regressions, Bash 3.2/5 syntax,
JavaScript syntax, `mvn -B clean install`, TypeScript tests/build/pack and the
TypeScript example build. Logs are under `logs/verification/`. Any failed check
makes the entry point exit nonzero; skipped checks have a separate counter.

Direct Java commands:

```bash
mvn clean install                         # complete reactor, including JUnit
mvn -Punit-tests test                      # excludes *IntegrationTest on the server
mvn -Pintegration-tests test               # integration subset
mvn -Pcode-quality -DskipTests verify       # formatting and static analysis
```

External tests are disabled by default. Ordinary green Maven results therefore
exclude live Ollama/OpenSearch quality. After preparing models and seeded data:

```bash
QWENBRIDGE_RUN_OLLAMA_IT=true QWENBRIDGE_RUN_OPENSEARCH_IT=true QWENBRIDGE_RUN_REDIS_IT=true mvn verify
```

## Live release checks

```bash
bash scripts/verify-release.sh
# From scripts/: bash verify-release.sh
```

For **all checks in one command**, including live integration tests, static
analysis and all seven k6 workload entrypoints with the default million-request load
(including multilingual commerce correctness and commerce throughput):

```bash
bash scripts/qwenbridge-final-verification.sh
# From scripts/: bash qwenbridge-final-verification.sh
```

This entry point prepares Docker and seeds a fresh index before enabling the
Ollama, OpenSearch and Redis JUnit tests (including `RedisConcurrentAccessIntegrationTest`).
It runs `verify-local.sh` and the code-quality profile, then release/API/SSE
checks (including V2 filters, refinement, evidence and finite SSE), staged performance,
and security/language/retrieval/resilience/commerce k6 cases. An isolated secured
OpenSearch container also verifies trusted TLS, credentials and filtered execution.
Any failure is retained. A separate `mvn clean install` beforehand is optional:
the complete entry point already performs it. For a shorter load while keeping
the other checks: `PERF_TOTAL_REQUESTS=1000 PERF_VUS=10 bash scripts/qwenbridge-final-verification.sh`.

The current complete commerce run **20261031-094646** passed: 681 Java and 36
TypeScript tests, static analysis, TLS/authentication/timeout checks, live V2
REST/SSE and all seven k6 workloads. The million-request stage used 250 VUs with
zero errors and p95 50.89 ms; cold p95 was 3.14 s. See the
[19-phase acceptance record](../platform/commerce-acceptance-2026-10-31.md) for
scope, all workload evidence and restored limits.

The earlier relocated entry point was verified end to end on 2026-10-31 with
that shorter load: 621 Java tests, 33 TypeScript tests, static analysis and all
five k6 scripts passed; the release summary was **72 passed, 0 failed, 0 skipped**.
Evidence: `logs/verification/verify-release-20261031-012220.log` and
`logs/verification/performance-20261031-012220/results.json`.
The separate [million-request result](../platform/performance-optimization-2026-10-31.md)
remains preserved.

This runs local checks, builds the Java 25 Quarkus fast-jar image, starts local
Redis/OpenSearch/Ollama, loads Qwen/BGE-M3 models, seeds the demo index and tests
health, API, error contracts, languages, security, retrieval, cache and SSE.
The `production` Compose profile is a historical name for this local stack;
OpenSearch authentication is disabled. Startup/model downloads may take time.

Volumes are preserved by default. `FORCE_FRESH=true` explicitly deletes disposable
stack volumes. Each release run uses a fresh `qwenbridge-verify-<run-id>` index and
a dedicated Redis cache prefix. Seeding refuses to overwrite an existing index;
resetting one requires `QWENBRIDGE_SEED_RESET_INDEX=true`. Cache checks unlink only
keys under the selected prefix. Use a disposable local stack.

`RELEASE_LIGHTWEIGHT=true` invokes local verification only.
`RUN_LOCAL_VERIFICATION=false` skips a previously completed local suite and runs
runtime checks. `RUN_DOCKER_RUNTIME=false` explicitly skips runtime verification;
that skip must not be presented as proof of a live deployment.

Files under `scripts/verification/00-*.sh` through `99-*.sh` are sourced helpers,
not standalone test entry points. `opensearch-seed.sh` needs running OpenSearch,
Ollama and the BGE-M3 model. The performance `.js` files require k6, not Bash.

## Performance checks

Start Docker Desktop, allocate resources for both models (this run uses 8 CPUs and
24 GB VM memory), then run this entry point from the project root:

```bash
bash scripts/verify-performance.sh
# From scripts/: bash verify-performance.sh
# Shorter check of the same flow:
PERF_TOTAL_REQUESTS=1000 PERF_VUS=10 bash scripts/verify-performance.sh
```

This builds/starts the Compose stack, seeds a fresh demo index, runs release
checks, then measures ten distinct cold-cache queries at one VU. It continues
with cached stages of 1,000 requests / 10 VUs, 10,000 / 50 VUs, a 10,000-request
check at the requested concurrency (when above 50 VUs), and finally
1,000,000 / 250 VUs. Throughput stages repeat the same ten multilingual queries.
One million is the total request count, not simultaneous users or unique queries.
Every requested HTTP call must finish; a timeout or incomplete run fails.
The default p95 < 5 s, p99 < 15 s and 100% semantic/contract checks stay enforced.
A cold-cache latency failure is retained even if the cached load passes;
contract errors or a failing cached stage stop further load escalation.
Any Redis pool rejection or cache fallback also fails the stage.

The benchmark grants its local app a temporary rate-limit budget, then recreates
the app with normal configured quotas on exit. It preserves volumes and only
clears keys in its isolated cache prefix. JSON summaries and k6 logs are under
`logs/verification/performance-<run-id>/`. The runner uses Dockerized k6 by
default; `PERF_RUNNER=native` uses an installed binary. `PERF_MAX_DURATION=30m`
limits each stage. `RUN_LOCAL_VERIFICATION=false` skips local tests when they
have already passed. `RUN_V9_PERFORMANCE=true bash scripts/verify-release.sh`
delegates to this same managed flow.

Model weights remain resident for ten minutes and up to two models can coexist.
The Docker defaults set a 12 GB limit for Ollama and 2 GB for OpenSearch;
these are configurable in `.env`. Cache-cold measurements use resident models;
first-ever model download and process startup are separate release checks.
The Redis pool uses 32 connections and a bounded queue of 1,024 acquisitions;
the default queue of 24 rejected cached reads at 250 VUs and caused unnecessary
model inference. A live Redis regression tests four waves of 250 concurrent reads.
The internal schema-constrained prompt reduces output tokens while retaining
the public analysis contract. Set `QWENBRIDGE_ANALYSIS_COMPACT_OUTPUT=false` to
restore the legacy prompt; cache format version `v5` isolates the new analyses.
Product documents omit the `embedding` array by default. Set
`QWENBRIDGE_SEARCH_OPENSEARCH_INCLUDE_EMBEDDINGS_IN_RESULTS=true` only when a
client needs it. Keyword/vector/hybrid retrieval still uses backend vectors.

Individual quality scripts can also run against a selected test environment:

```bash
PERF_TOTAL_REQUESTS=11 PERF_VUS=1 PERF_MAX_DURATION=10m k6 run scripts/performance/k6-language-quality-test.js
```

Run each file under `scripts/performance/` against a selected local test environment.
Every script has a `checks` threshold, so semantic assertion failures fail the run.
Cases cycle deterministically so small runs can cover the full input corpus.
The mixed-language load script retains its original p95 < 5s and p99 < 15s latency
thresholds; cold AI inference can exceed these even when every response is correct.
The resilience script treats expected validation errors as expected HTTP statuses.
These small runs establish smoke coverage only, not load capacity.

## Product coverage

See [the 50-phase evidence matrix](../platform/phase-acceptance.md). Provider mocks,
source-file existence checks and passing JUnit tests do not establish completion
of missing commerce, pack, licensing or Machine-to-Human features.

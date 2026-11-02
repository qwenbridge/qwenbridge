# QwenBridge 1.0: Section 1 Completion Report (Universal Intelligence Core)

> **Historical record.** It describes the repository at the time it was written. Modules and connectors it mentions may have been removed since: QB-287 removed the Elasticsearch, PostgreSQL, MongoDB and Shopify connector modules and `examples/` (ADR 0058). For the current state, start at [docs/README.md](../README.md).

**Scope:** QB-001 – QB-084, Phases 1–12
**Branch / base:** `quarkus-branch` @ `27b7f39`. All changes are local and
uncommitted.
**Git commits: 0. Git pushes: 0.**

## 1. Gate result

| Item | Result |
|---|---|
| Final master run | `section1-final-b-20261101-010728`, **PASS**, exit 0 |
| Workload | 1,000,000 requests at 250 VUs (full default). Cached load: 1,000,000/1,000,000 completed, HTTP error rate 0, 6,000,000/6,000,000 k6 checks passed, p95 119 ms, p99 156 ms. Cold, cache-miss path (live model): p95 3.10 s. Commerce throughput: 10,000 requests at 100 VUs, p95 159 ms, 0 errors. |
| Checks | 82 passed, 0 failed, 0 skipped, 2 warnings (uncommitted working tree; tag v0.9.1 not on HEAD) |
| Tests | Core 487, server 867, Java SDK 62; live Ollama Commerce 41/41 |
| Code quality | SpotBugs 0 findings in every module; Spotless clean |

An earlier full run, `section1-final-20261101-005620`, failed on the new SDK
check. The cause was a defect in the new gate script: Maven resolved
`-DoutputFile` against each module directory, so the classpath file and the
dependency listings were missing. Two checks had passed on missing files. The
script was fixed to use absolute paths, to fail on missing or empty listings,
and to require a listing for every module. That run also skipped the load phase
because an earlier step had failed. The run above is the corrected one.

**Section 1 gate checks added to the master script**
(`scripts/verification/18-section1-gate.sh`):

1. **Core dependency isolation.** The only runtime dependencies are the Jackson
   core, databind and annotations artifacts. Core imports no framework,
   connector or server package.
2. **Spring Boot elimination.** No Spring source, configuration, Dockerfile
   reference or resolved artifact exists anywhere in the reactor.
3. **Startup without Redis, OpenSearch or Ollama.** The packaged server, with
   all three unreachable:
   - starts, with liveness 200 and `/api/v1/health` 200;
   - answers `/api/v1/search/analyze` with 200 in under a second;
   - degrades safely: `decision=BLOCK`, `threatAssessment.modelStatus=UNAVAILABLE`,
     overall confidence `UNKNOWN`;
   - logs no errors.
4. **Java SDK live analyze.** The published SDK calls the running server; the
   request ID is echoed and the response satisfies the SDK contract.

## 2. Task status

72 VERIFIED, 12 PARTIAL, 0 not started. "VERIFIED" means implemented, covered by
tests and exercised by a passing master run. Every PARTIAL item states what is
missing and where it is planned.

| Phase | Tasks | Status |
|---|---|---|
| 1 Universal request & context | QB-001 – QB-007 | all VERIFIED except QB-006 |
| 2 Language & normalization | QB-008 – QB-014 | all VERIFIED except QB-013 |
| 3 Translation | QB-015 – QB-021 | all VERIFIED except QB-017, QB-018 |
| 4 Intent | QB-022 – QB-028 | all VERIFIED except QB-026, QB-028 |
| 5 Extraction | QB-029 – QB-035 | all VERIFIED except QB-031 |
| 6 QueryIR | QB-036 – QB-042 | all VERIFIED |
| 7 Schema registry | QB-043 – QB-049 | all VERIFIED except QB-047 |
| 8 Constraint validation gate | QB-050 – QB-056 | all VERIFIED except QB-053 |
| 9 Request validation & safety | QB-057 – QB-063 | all VERIFIED except QB-061 |
| 10 Threat detection | QB-064 – QB-070 | all VERIFIED except QB-070 |
| 11 Policy & authorization | QB-071 – QB-077 | all VERIFIED (QB-075 enforced behind a flag) |
| 12 Confidence, evidence & explanations | QB-078 – QB-084 | all VERIFIED except QB-083 |

### PARTIAL items

| Task | What is done | What is missing, and where |
|---|---|---|
| QB-006 Request tracing | IDs on REST, errors and SSE; validated echo | Propagation to outbound backend calls (QB-184, S3) |
| QB-013 Mixed script/language | Script segmentation with a language per segment | Same-script language mixing (Swedish/English) is not split |
| QB-017 Text representations | Original, normalized and translated forms with derivations in Core and v1 | Commerce v2 keeps its own `Translation` record |
| QB-018 Translation preservation | All kinds checked; numbers, units, entities and identifiers in any language | Operators, negation and currency strictly checked only for en/sv/fa; Commerce judges only losses |
| QB-026 Ambiguous / out-of-domain | Vague and disabled-domain statuses | Out-of-domain detection without a hint needs domain vocabularies (S4) |
| QB-028 Intent tests | Contract and REST tests | Measured accuracy needs a labelled dataset (H7) |
| QB-031 Time and place | Relative windows, ISO bounds, countries | Natural-language dates, month windows, cities |
| QB-047 Pack schema registration | Ownership rules; built-in packs | Externally installed packs' activate/deactivate (QB-233, S4) |
| QB-053 Semantic mapping | Typed mapping rules | Commerce rules live in the server until the pack vocabulary SPI exists |
| QB-061 Redaction | Messages redacted, control characters escaped | Exception text inside stack traces is not rewritten |
| QB-070 Threat metrics | Seed set and metrics; floors in the test | Representative, independently labelled dataset (H7) |
| QB-083 Calibration | Core Brier, ECE and bins; live intent-score report | Labelled set of 50 or more with wrong answers; ECE withheld at 31 (H7) |

## 3. Decisions taken with the user during Section 1

- Java 25; a new `qwenbridge-core` module; Jackson is the only Core dependency.
- Batch-by-batch authorization with a report after each batch. Intermediate
  master runs used 1000 requests / 10 VUs; this gate uses the full 1M / 250.
- `06-sse.sh` FLUSHDB was replaced by a scoped cache clear. The Table 1
  compose file was recreated (postgres:16, mongo:8.0, elasticsearch:8.19.23).
- Approvals for high-risk operations are classified, validated and reported;
  enforcement sits behind `qwenbridge.policy.approvals-enforced` (default
  false).
- Authentication is required in `%prod` only; dev, test and docker keep
  anonymous search with a startup warning (audit S3).
- Domain Pack policies only restrict; the base default-deny is authentication,
  tenant and scope (audit S2).

## 4. Behaviour changes a client can observe

All changes are additive unless listed here.

- AI and search provider errors return the fixed messages "AI provider failure"
  and "Search provider failure"; status and code are unchanged. The Ollama
  response body is no longer echoed (S6).
- A request body without a JSON content type gets 415. Bodies over 64 KiB get
  413 on every profile; upload routes allow 4 MB. Previously the limit applied
  only when abuse protection was on.
- A JSON `null` body gets 400 (previously 500). Binding errors return the
  standard `ApiError`.
- Swedish and Persian prompt-injection phrases are blocked.
- The `%prod` profile refuses to start without configured principals.
- Unknown operations are denied by the central policy engine.
- New v1 response fields: `queryIR`, `unresolved`, `constraintValidation`,
  `translation`, `textRepresentations`, `threatAssessment`,
  `confidenceReport`, `explanations`. The SDK contract fixture is refreshed.

## 5. Audit security findings

| Finding | Status |
|---|---|
| S2 default-allow pack policies | Fixed |
| S3 anonymous default | Fixed for `%prod` |
| S4 rule loader fails open | Fixed |
| S5 rate-limit key collisions | Fixed (SHA-256) |
| S6 error messages echoed | Fixed |
| S7 no redaction / parser limits | Fixed |
| S12 repeated URL decoding | Unchanged: intentional, used only for threat canonicalization |
| S1 CORS wide open | Open, outside Phase 1–12 scope |
| S8 pack code runs at install | Open (S4) |
| S9 unsigned plugins | Open (S4) |
| S10 `failBuildOnCVSS=11` | Open |
| S11 fixed-seed test key | Open (test only) |
| S13 key rotation | Open (S4) |
| S14 connectors have no read-only mode | Open (S2) |

## 6. Known gaps carried into Section 2

- **Readiness and Redis.** Readiness reports DOWN when Redis is configured but
  unreachable. Redis still backs platform storage, the Commerce schema
  registry, evidence, analytics and the analysis cache, and the Quarkus Redis
  health check cannot be switched off at runtime. Making Redis an optional
  connector is QB-149.
- **Understanding stops without the model.** When the model is unavailable,
  the v1 pipeline stops before QueryIR assembly (the execution control stops
  the pipeline). Core understanding could still run there.
- **The constraint gate reports but does not enforce** (`enforced:false`). The
  Section 2 compilers consume `ValidatedQuery`.
- **Free-text exclusions block without a `content` field.** They become
  INVALID_HARD against schemas that have no such field; Section 2 needs a
  full-text capability.
- **Approvals cover a resource type.** They bind an operation to a resource
  type, not to a named resource, and are kept in memory.
- **The model's intent confidence is constant.** It returned 0.9 for all 31 live
  cases, so it carries no information.

## 7. Evidence

- Logs: `logs/qwenbridge-1.0/*.console.log` with `*.meta` (run id and exit
  code) for every batch and for this gate.
- ADRs 0014 – 0024 in `docs/adr/`.
- Reports produced by tests:
  - `qwenbridge-server/target/threat-eval-report.json`
  - `qwenbridge-server/target/calibration-report.json`
  - `qwenbridge-server/target/commerce-language-report.jsonl`
  - `qwenbridge-server/target/contract/v1-analyze-response.json`

**Section 2 has not been started.** It waits for explicit authorization.

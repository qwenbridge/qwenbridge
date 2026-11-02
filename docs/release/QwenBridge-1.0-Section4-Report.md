# QwenBridge 1.0: Section 4 Completion Report (Plugins, Agents and Commercial Foundation)

> **Historical record.** It describes the repository at the time it was written. Modules and connectors it mentions may have been removed since: QB-287 removed the Elasticsearch, PostgreSQL, MongoDB and Shopify connector modules and `examples/` (ADR 0058). For the current state, start at [docs/README.md](../README.md).

**Scope:** QB-225 – QB-285, Phases 31–39 (61 tasks)
**Branch / base:** `quarkus-branch` @ `27b7f39`. All changes are local and uncommitted.
**Git commits: 0. Git pushes: 0.**

## 1. Final gate

| Item | Result |
|---|---|
| Final master run | `section4-final-20261102-091819`, **PASS**, exit 0 |
| Cached load (default) | 1,000,000/1,000,000 completed; HTTP error rate 0; 6,000,000/6,000,000 checks passed; 2,565 req/s over 392 s; p95 125 ms, p99 160 ms. Heap max 211 MB; container max 649 MB of 1,024 MB; 3.0 Redis commands per request |
| Other load phases | 10,000 requests at 250 VUs: 2,263 req/s, p95 138 ms. Commerce throughput, 10,000 requests at 100 VUs: 1,075 req/s, p95 171 ms, 0 errors (26.4 Redis commands per request). Cold path (live model): p95 3.18 s |
| Quality phases (live model) | Commerce quality 186/186, language 22/22, retrieval 15/15, security input 18/18, resilience 8/8, Table 1 sessions 280/280 |
| Regression gate | 0 failing of 18. 1 M cached p95 +5.7 %; Commerce throughput p95 +26.7 %; Core keyword +23.4 %; vector and hybrid within 4 %; quality nDCG@5 unchanged. The injected-slowdown check was detected (+180 %) |
| Checks | 88 passed, 0 failed, 0 skipped, 2 warnings (uncommitted working tree; tag v0.9.1 not on HEAD) |
| Tests | Core 589; server 1,009 (1 skipped: the opt-in AI benchmark); Java SDK 72; TypeScript SDK and browser tests passing; connector modules passing (Shopify sandbox skipped: no credentials) |
| Code quality | SpotBugs 0 findings; Spotless clean |

## 2. Batches

Intermediate runs used 1,000 requests at 10 VUs.

| Batch | Tasks | ADR | Master run | Result |
|---|---|---|---|---|
| 31 | QB-225 – 231 Plugin SDK and Agent SPI | 0046 | `batch31-master-20261101-224449` | PASS |
| 32 | QB-232 – 242 Domain and Connector Packs | 0047 | `batch32-master-20261101-231643` | PASS |
| 33 | QB-243 – 248 AI provider SPIs | 0048 | `batch33-master-b-20261101-235203` | PASS (second run; §5) |
| 34 | QB-249 – 253 Policy Packs | 0049 | `batch34-master-b-20261102-002404` | PASS (second run; §5) |
| 35 | QB-254 – 260 Local pack registry and lifecycle | 0050 | `batch35-master-b-20261102-081346` | PASS (second run; §5) |
| 36 | QB-261 – 273 Licensing and editions | 0051 | `batch36-master-20261102-083447` | PASS |
| 37 | QB-274 – 279 Pack distribution and RC checklist | 0052 | `batch37-master-20261102-085540` | PASS |
| 38 | QB-280 – 285 Regression, audits, documentation, gate | — | `section4-final-20261102-091819` | PASS |

## 3. Task status: 59 VERIFIED, 2 PARTIAL

"VERIFIED" means implemented, covered by tests and exercised by a passing master run.

| Phase | Tasks | Status |
|---|---|---|
| 31 Plugin SDK and Agent SPI | QB-225 – 231 | All VERIFIED. There is no sandbox for plugin code; this is documented as a limit, not hidden |
| 32 Domain Packs | QB-232 – 236 | All VERIFIED. The server's own Commerce requests do not call extension validation on every request |
| 33 Connector Packs | QB-237 – 242 | All VERIFIED. Commerce search still uses its built-in backends (FU-12) |
| 34 AI provider packs | QB-243 – 248 | All VERIFIED |
| 35 Policy Packs | QB-249 – 253 | All VERIFIED |
| 36 Pack registry and lifecycle | QB-254 – 260 | All VERIFIED |
| 37 Licensing | QB-261 – 267 | All VERIFIED |
| 38 Editions | QB-268 – 273 | VERIFIED except **QB-273 PARTIAL**: gates are consistent across API, packs and execution paths; SDKs only see the standard 403 error and have no licensing API. The edition matrix is a proposal for the owner (`docs/editions.md`) |
| 39 Marketplace and release | QB-274 – 285 | VERIFIED except **QB-281 PARTIAL**: Trivy is clean, but the NVD (OWASP) scan is blocked offline. QB-285 is VERIFIED as executed; its decision is NO-GO (§6) |

## 4. Section 4 integration scenarios

| Scenario | Evidence | Result |
|---|---|---|
| A Core without packs | `CleanStartTest` | PASS |
| B Commerce Pack activation and removal | `DomainPackLifecycleTest` | PASS |
| C Connector Pack failure | `ConnectorPacksTest`, `ConnectorPackOutageTest` | PASS |
| D Agent authorization | `AgentAuthorizationTest` | PASS |
| E Prompt injection against an agent | `AgentAuthorizationTest` | PASS |
| F Embedding provider switching | `AiProviderSwitchingTest` | PASS |
| G Policy conflict | `PolicyPackEnforcementTest` | PASS |
| H Pack integrity | `PackLifecycleTest`, `PackDistributionTest` | PASS |
| I Offline license | `LicensingAndEditionsTest`, `OfflinePackRegistryApiTest` | PASS |
| J Edition downgrade | `LicensingAndEditionsTest` | PASS |
| K Cross-tenant isolation | `AgentAuthorizationTest`, `AuthorizationGateTest`, `PackLifecycleTest.packsAreTenantScoped` | PASS |
| L Clean installation | `CleanStartTest`, `ServiceFreeE2ETest` | PASS |

## 5. Failed runs and their causes

| Run | Cause | Fix |
|---|---|---|
| `batch33-master-20261101-234250` | **My change.** `/api/v1/ai/chat` stopped calling `AIService.chat`, which existing tests mock, so a provider failure returned 500 instead of 502 | The controller keeps `chat()`; which provider answered is reported through a thread-local. No test changed |
| `batch34-master-20261102-001147` | One Java SDK test (`shouldSerializeMultilingualRequestMetadata`) got an empty body from its local stub server. The SDK was untouched, and 6 of 6 isolated reruns passed | Rerun without changes; recorded as transient |
| `batch35-master-20261102-005033` | The Mac slept on battery during the run (power log: maintenance sleep, full wake 08:09). Two live-model tests timed out and failed closed (BLOCK) | Rerun awake. Later runs use `caffeinate -i` |

A finding found and fixed in B38: `/q/health/ready` reported the server DOWN without Redis, through
Quarkus's built-in Redis check. Redis is optional, so the built-in check is disabled. Redis is still
reported as a DEGRADED dependency in `/api/v1/health/ready`.

## 6. Release decision

**NO-GO** for QwenBridge 1.0. The engineering gate passes; the blockers are listed in
`QwenBridge-1.0-Release-Decision.md`.

## 7. Documents

| Kind | Documents |
|---|---|
| ADRs | 0046 – 0052 |
| Architecture | `packs.md`, `ai-providers.md`, `pack-registry.md`, `pack-distribution.md` |
| Security | `plugin-isolation.md`, `policy-packs.md`, `licensing.md`, `QwenBridge-1.0-Security-Audit.md` |
| Editions | `docs/editions.md` |
| Release | `QwenBridge-1.0-RC-Checklist.md`, `-Compatibility.md`, `-Release-Notes.md`, `-Release-Decision.md` |
| API | `docs/api/rest-api.md` (extensions and agents, packs, AI models) |

**New endpoints** (8):
- `/api/v2/extensions`;
- `/api/v2/agents`, `/api/v2/agents/{id}/runs`, `/api/v2/agents/runs/{id}` (GET and DELETE);
- `/api/v2/domain-packs`, `/api/v2/connector-packs`;
- `/api/v2/ai/models`.

All are additive, reviewed in `api-endpoints.txt` and documented.

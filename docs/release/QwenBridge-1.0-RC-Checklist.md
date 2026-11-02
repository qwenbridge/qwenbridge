# QwenBridge 1.0: Release Candidate Checklist

> **Historical record.** It describes the repository at the time it was written. Modules and connectors it mentions may have been removed since: QB-287 removed the Elasticsearch, PostgreSQL, MongoDB and Shopify connector modules and `examples/` (ADR 0058). For the current state, start at [docs/README.md](../README.md).

Release readiness is decided from recorded evidence, not opinion (QB-279):
- every gate names who or what is responsible, how it is verified, and the recorded outcome;
- a gate without a recorded passing outcome blocks the release;
- the outcomes below were recorded by the final regression and release gate (QB-280 – QB-285).

**Responsible roles:**

| Role | Is |
|---|---|
| Release owner | The repository owner, who signs off the GO or NO-GO |
| Master verification | `scripts/qwenbridge-final-verification.sh` |
| Regression gate | `scripts/verification/regression_gate.py` against `evaluation/baselines/baseline.json` |

## 1. Functionality

| # | Gate | Responsible | Verification | Outcome |
|---|---|---|---|---|
| F1 | All 285 tasks classified with evidence | Release owner | Section 1–4 reports (`docs/release/QwenBridge-1.0-Section*-Report.md`) | Recorded: 258 VERIFIED, 27 PARTIAL (Section reports 1–4) |
| F2 | Keyword, vector and hybrid retrieval functionally tested | Master verification | Core retrieval tests; `RetrievalQualityEvaluationTest` (live model); live strategy comparison step | PASS (`section4-final-20261102-091819`; nDCG@5 0.81 / 0.98 / 0.93) |
| F3 | Universal QueryIR stays domain-neutral | Master verification | `CommerceSeparationTest`, `DomainPackLifecycleTest` (extensions without touching Core fields) | PASS in every Section 4 batch |
| F4 | Commerce and external integrations optional | Master verification | `DomainPackLifecycleTest` (any subset of domains), `PacksApiTest` (all Connector Packs disabled), clean-start test | PASS (`CleanStartTest`) |

## 2. Section 4 integration scenarios

| Scenario | Evidence | Outcome |
|---|---|---|
| A Core without packs | `CleanStartTest` (domains, Connector Packs and agents disabled) | PASS (B38) |
| B Commerce Pack activation and removal | `DomainPackLifecycleTest.disablingCommerceLeavesOtherDomainsWorking` | PASS (B32) |
| C Connector Pack failure | `ConnectorPacksTest.failuresStayInsideTheirPack`; live outage test of an enabled OpenSearch Connector Pack | PASS (B32; live `ConnectorPackOutageTest` B38) |
| D Agent authorization | `AgentAuthorizationTest.unauthorizedAndMutatingToolCallsAreDeniedBeforeExecution` | PASS (B31) |
| E Prompt injection against an agent | `AgentAuthorizationTest.promptInjectionAndForgedApprovalsGrantNothing` | PASS (B31) |
| F Embedding provider switching | `AiProviderSwitchingTest.embeddingProvidersAreInterchangeableOnlyWithinOneSpace` | PASS (B33) |
| G Policy conflict | `PolicyPackEnforcementTest.packsContributeRulesInADeterministicRestrictiveOrder` | PASS (B34) |
| H Pack integrity | `PackLifecycleTest`, `PackDistributionTest.corruptedUntrustedAlteredAndRevokedPackagesAreRefused` | PASS (B35, B37) |
| I Offline license | `LicensingAndEditionsTest`; `OfflinePackRegistryApiTest` (no license server) | PASS (B36) |
| J Edition downgrade | `LicensingAndEditionsTest.editionsGatePacksCentrallyAndDowngradesSuspendWithoutDeleting` | PASS (B36) |
| K Cross-tenant isolation | `AgentAuthorizationTest` (other tenant: 404), `AuthorizationGateTest.connectorsAndPluginsCannotReachAnotherTenant`, `PackLifecycleTest.packsAreTenantScoped` | PASS (B38) |
| L Clean installation | `CleanStartTest`, `ServiceFreeE2ETest` | PASS (B38) |

## 3. Compatibility

| # | Gate | Responsible | Verification | Outcome |
|---|---|---|---|---|
| C1 | REST endpoint list unchanged except reviewed additions | Master verification | `ApiEndpointSnapshotTest` | PASS every batch |
| C2 | Java and TypeScript SDKs against the live server | Master verification | Section 3 SDK steps (`19-section3-sdk.sh`) | PASS every batch |
| C3 | Plugin SDK, Connector SPI, pack, policy and license contracts | Master verification | `PluginHostTest`, `ConnectorPacksTest`, `PackLifecycleTest`, `PolicyPackEnforcementTest`, `LicensingAndEditionsTest` | PASS; `QwenBridge-1.0-Compatibility.md` |

## 4. Tests

| # | Gate | Responsible | Verification | Outcome |
|---|---|---|---|---|
| T1 | Full regression of Phases 1–39, with skipped tests explained | Master verification | Final gate run at the default load | PASS (`section4-final-20261102-091819`; 88/0/0; 1 opt-in benchmark skipped) |
| T2 | No failing test; no test removed or weakened to pass | Release owner | Master logs and registry of fixes | Recorded per batch |

## 5. Security

| # | Gate | Responsible | Verification | Outcome |
|---|---|---|---|---|
| S1 | Dependency vulnerabilities | Release owner | OWASP dependency-check and Trivy, where their databases are available | Trivy: 0 CRITICAL or HIGH in 195 targets; OWASP NVD: **BLOCKED** |
| S2 | Authentication, authorization, input validation, injection, plugin trust, signatures, licensing | Release owner | Security audit (QB-281) against the test evidence | Done: `docs/security/QwenBridge-1.0-Security-Audit.md` |
| S3 | Open findings from earlier audits | Release owner | `docs/roadmap/QwenBridge-1.0-Followups.md` FU-22 (S1, S8–S11, S13) | **Open** (S1, S1b, S10; S9 owner decision) |

## 6. Dependencies on external services

| # | Gate | Responsible | Verification | Outcome |
|---|---|---|---|---|
| D1 | Baseline Core without PostgreSQL, MongoDB, Redis, Elasticsearch, OpenSearch, Shopify or a license server | Master verification | `CleanStartTest` (QB-282) | PASS |

## 7. Documentation

| # | Gate | Responsible | Verification | Outcome |
|---|---|---|---|---|
| G1 | Release notes and architecture documentation match the verified behaviour | Release owner | Review against ADRs 0001–0052 and test evidence (QB-284) | Done: `QwenBridge-1.0-Release-Notes.md` |
| G2 | Limitations documented | Release owner | `docs/security/plugin-isolation.md`, `docs/security/licensing.md`, `QwenBridge-1.0-Followups.md` | Present |

## 8. Licensing

| # | Gate | Responsible | Verification | Outcome |
|---|---|---|---|---|
| L1 | Offline license verification and edition gates | Master verification | `LicensingAndEditionsTest` | PASS (B36) |
| L2 | Edition matrix confirmed by the product owner | Release owner | `docs/editions.md` (marked proposal) | Pending owner decision |

## 9. Performance

| # | Gate | Responsible | Verification | Outcome |
|---|---|---|---|---|
| P1 | Regression gate (18 baselines) | Regression gate | Final gate run | PASS (0 of 18 failing) |
| P2 | 1,000,000 cached requests at 250 VUs, 0 errors | Master verification | Final gate run at the default load | PASS (1,000,000/1,000,000; 0 errors; p95 125 ms) |

## Decision

**NO-GO**: see `QwenBridge-1.0-Release-Decision.md`. Open gates: security rows S1 (NVD scan blocked) and S3 (open findings), L2 (edition matrix), and F1 (27 PARTIAL tasks).

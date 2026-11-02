# QwenBridge 1.0: Release Decision (QB-285)

**Decision: NO-GO.**
**Date:** 2 November 2026.
**Evidence run:** `section4-final-20261102-091819`.

The engineering gate passes: every mandatory test, compatibility check, offline and clean-start
scenario, and performance threshold. The release is still not approved, because some required
gates are unverified or unresolved, and the roadmap forbids approving a release in that state.

## Final Release Gate criteria

| Criterion | Status | Evidence |
|---|---|---|
| All mandatory requirements across Phases 1–39 verified | **Not met** | 258 of 285 tasks VERIFIED; 27 PARTIAL (`QwenBridge-1.0-Release-Notes.md`, *Known limitations*) |
| Keyword, vector and hybrid retrieval functionally tested | Met | Core retrieval tests; live quality evaluation (nDCG@5 0.81 / 0.98 / 0.93); live strategy comparison |
| Universal QueryIR domain-neutral | Met | `CommerceSeparationTest`, `DomainPackLifecycleTest` |
| Commerce and external integrations optional | Met | `CleanStartTest`, `PacksApiTest`, `DomainPackLifecycleTest` |
| No external database or license server for baseline Core startup | Met | `CleanStartTest`, after the readiness fix in B38 |
| Agent and Connector execution respect central authorization | Met | `AgentAuthorizationTest`, `ConnectorPacksTest`, `AuthorizationGateTest` |
| Plugin and Pack trust limitations documented accurately | Met | `docs/security/plugin-isolation.md`, ADRs 0046, 0050, 0052 |
| Offline license verification | Met | `LicensingAndEditionsTest` |
| API, SDK and Pack compatibility checks pass | Met | `QwenBridge-1.0-Compatibility.md`; final master run |
| Security audits meet the approved release criteria | **Not met** | No approved release policy exists; findings S1, S1b and S10 are open; the NVD scan is blocked (`docs/security/QwenBridge-1.0-Security-Audit.md`) |
| Performance and resilience meet measurable thresholds | Met (laptop baselines) | Regression gate 0 of 18 failing; 1 M requests, 0 errors |
| Release documentation matches behaviour | Met | Release notes, ADRs, compatibility matrix, audit |

## Blockers, and what turns each into GO

| # | Blocker | Needed for GO | Owner |
|---|---|---|---|
| 1 | **The NVD dependency audit is BLOCKED** (QB-281). The local cache is unusable offline | Run OWASP Dependency-Check with a current NVD database (CI has the job and an NVD key). Raise the threshold so it can fail (S10: `failBuildOnCVSS=11` cannot) | Release owner |
| 2 | **Open security findings** | Fix or formally accept, under a written release policy:<br>• **S1 (CORS):** set explicit origins in `%prod`<br>• **S1b:** the production configuration validator checks the profile `production`, but Quarkus uses `prod`<br>• **S9:** unsigned plugin directories | Release owner |
| 3 | **P1 follow-ups:** FU-01 (abandoned model work), FU-02 (translation cache), FU-03 (language misdetection), FU-15 (request scope in v1). The follow-up list marks them "before any production or pilot deployment" | Fix, or accept them in the release policy for a non-production 1.0 | Release owner |
| 4 | **Owner decisions pending** | Confirm the edition matrix (`docs/editions.md`, a proposal); decide FU-04 (degraded mode) and FU-09 (production store) | Product owner |
| 5 | **27 PARTIAL tasks** | Complete them, or explicitly descope each one in the release policy | Release owner |
| 6 | **The release artifact is not reproducible from version control** | Commit the work and tag the release (`v1.0.0`) on that commit, then rerun the gate on it. All changes are local and uncommitted by instruction; the gate warns about this | Release owner |

## What GO would require

1. The blockers above are resolved or formally accepted by the release owner in a written
   release policy.
2. The final gate runs again on the tagged commit.
3. The RC checklist (`QwenBridge-1.0-RC-Checklist.md`) has every outcome recorded as passing or
   accepted.

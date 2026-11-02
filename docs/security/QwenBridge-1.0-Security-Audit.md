# QwenBridge 1.0: Security and Dependency Audit (QB-281)

**Date:** 2 November 2026.
**Scope:** the branch `quarkus-branch` working tree (uncommitted) and the image
`quarkus-qwenbridge-app` built from it.
**Evidence:** command outputs are in `logs/qwenbridge-1.0/audit/`.

## 1. Tools

| Check | Tool and data | Result |
|---|---|---|
| Container and JAR vulnerabilities | Trivy 0.69.3, offline. Vulnerability DB of 26 Oct 2026, Java DB of 26 Oct 2026. `CRITICAL,HIGH` with `.trivyignore`, which has no exceptions | **0 findings in 195 targets** (Ubuntu 26.04 packages, every application JAR, one Go binary) |
| Java dependency vulnerabilities (NVD) | OWASP Dependency-Check 10.0.4 and 13.0.0 with `autoUpdate=false` | **BLOCKED.** The local NVD cache cannot be used by either plugin version (`NoDataException`). Refreshing it needs an online NVD download, which was not done. CI runs this check with an NVD key |
| npm dependencies (TypeScript SDK) | `npm audit` in the master run | PASS |
| Static analysis | SpotBugs on every module | 0 findings |
| Hardcoded credentials | Pattern scan of main sources, configuration and Compose files; Trivy secret scanning of the image | None found. `.env.example` holds placeholders only |

The Trivy data is about one week old. A release build must repeat the scan with current
advisories. Trivy's JAR matching is not equivalent to an NVD (CPE) analysis.

## 2. Review against test evidence

| Area | Status | Evidence |
|---|---|---|
| Authentication | Required in `%prod`; startup fails without principals. Anonymous callers outside production can only search | `AuthorizationGateTest`, ADR 0023 |
| Authorization | One central engine for endpoints, connectors, plugins, agents and packs. Default deny; tenant, scope and approval checks; packs only restrict | `AuthorizationGateTest`, `AgentAuthorizationTest`, `PolicyPackEnforcementTest`, `DomainPackLifecycleTest` |
| Input validation and injection | Request validation, threat detection, QueryIR grounding; model text never grants | `RequestValidationPipelineTest`, `InjectionSafetyTest`, `AgentAuthorizationTest` |
| Plugin and pack trust | Trusted code in the server JVM, with no sandbox (documented). Signatures and publisher trust are verified before pack code is loaded | `docs/security/plugin-isolation.md`, `PackLifecycleTest`, `PackDistributionTest` |
| Package signatures | Ed25519; corrupted, re-signed, moved metadata, untrusted and revoked packages refused | `PackDistributionTest` |
| License verification | Offline Ed25519; typed refusals; only public keys accepted | `LicensingAndEditionsTest` |
| Secrets | Connector and pack secrets are references only; values never appear in statuses or logs | `ConnectorPacksTest`, `ConnectorPackOutageTest` |

## 3. Findings

| ID | Finding | Severity | Status |
|---|---|---|---|
| S1 | **CORS allows any origin.** `quarkus.http.cors.origins` is `/.*/`. `qwenbridge.security.cors.allowed-origin-patterns` is not applied to CORS, and its `%prod` default is `*`. Impact is limited because credentials are bearer tokens, not cookies, but any site can call the API from a browser with a token it holds | Medium | **Open** |
| S1b | **The production configuration validator never runs in production.** It checks the profile `production`, but Quarkus's production profile is `prod`, so missing production settings are not detected at startup | Medium | **Open (new in this audit)** |
| S8 | **Pack code runs at installation.** The signed code is loaded to verify the kind and manifest, so static initializers run. This now happens only after signature and trust checks; metadata inspection runs no code | Low (trusted, signed code) | Mitigated; accepted under the documented trust model, pending owner approval |
| S9 | **Unsigned plugins from a directory.** Legacy `PluginRegistry` jars and `qwenbridge.plugins.directory` jars are trusted through file-system permissions. Plugin SDK 1.0 checks manifests before loading and authorizes every tool call | Medium | Open (documented); owner decision |
| S10 | **CI never fails on a vulnerability.** CI runs Dependency-Check with `-DfailBuildOnCVSS=11` | Medium (process) | **Open** |
| S11 | **Reproducible test signing key.** A test signing key comes from a fixed `SHA1PRNG` seed (test code only) | Low | Open |
| S13 | **Key rotation.** Overlapping trusted keys and revocation lists now exist (ADRs 0051, 0052). A publisher's identity is its key id, so rotating a key changes the publisher id | Low | Partly resolved |
| R1 | **Readiness without Redis.** `/q/health/ready` reported the server DOWN when Redis was absent, although Redis is optional | Medium (availability) | **Fixed in this batch** |
| L1 | **Offline license clocks.** Validity uses the server clock, which an operator can set back | Low | Documented (`docs/security/licensing.md`) |

## 4. Conclusion

- **Critical or High findings:** none in the code review or in the Trivy scan.
- **What remains:**
  - **Open:** the S1 and S1b configuration findings and the S10 CI threshold.
  - **Blocked:** the NVD dependency scan.
- **Approval needed:** there is no approved release policy that accepts open findings, so the
  release owner must approve them. They are listed as blockers in the release decision
  (`docs/release/QwenBridge-1.0-Release-Decision.md`).

# QwenBridge Documentation

This index covers the current documentation for QwenBridge 1.0 (build 0.9.1). The **historical
records** (old roadmaps and release evidence) describe the repository at the time they were
written; each one carries a banner that says so.

## Start here

| Document | Content |
|---|---|
| [Getting started](development/getting-started.md) | Build, run database-free, first search, first SDK call |
| [Local development](development/local-development.md) | Prerequisites, dev mode, project layout |
| [Configuration](operations/configuration.md) | Every configuration area, environment variables, profiles |
| [Troubleshooting](operations/troubleshooting.md) | Common startup, model, search and SDK problems |

## Architecture

| Document | Content |
|---|---|
| [Overview](architecture/overview.md) | System, module graph, components, deployment and extension diagrams |
| [Class diagrams](architecture/class-diagrams.md) | Main types, taken from the source |
| [Flows](architecture/flows.md) | Request, language, spelling, expansion, retrieval, safety and operations flowcharts |
| [Sequence diagrams](architecture/sequences.md) | REST, SSE, SDK, cancellation, catalog sync and pack activation |
| [Remediation proposals](architecture/remediation-proposals.md) | Proposed follow-up migrations: OpenSearch extraction, v1 onto the Core engine, Redis-free catalog sync, Commerce separation |
| [Database-free runtime](architecture/database-free-runtime.md) | No mandatory database, search engine or cache |
| [Quarkus-only certification](architecture/quarkus-only.md) | Framework audit and guards |
| [Typo correction](architecture/typo-correction.md) | Dictionaries, modes, protected tokens, tenants |
| [Multilingual expansion](architecture/multilingual-expansion.md) | Target languages, providers, fusion |
| [Packs](architecture/packs.md), [registry](architecture/pack-registry.md), [distribution](architecture/pack-distribution.md) | Domain and connector packs, lifecycle, signing |
| [AI providers](architecture/ai-providers.md), [provider reliability](architecture/provider-reliability.md) | Model providers, routing, retries |
| [Pipeline](architecture/pipeline.md), [ranking policy](architecture/ranking-policy.md), [retrieval verification](architecture/retrieval-verification.md) | Legacy v1 pipeline, ranking, constraint checks |
| [Architecture rules](architecture/architecture-rules.md), [modules](architecture/modules.md) | Boundaries enforced by tests |
| [ADRs](adr/) | 58 architecture decision records; ADR 0058 records the QB-287 cleanup |

## APIs and SDKs

| Document | Content |
|---|---|
| [REST API](api/rest-api.md) | v2, platform and legacy v1 endpoints, errors |
| [SSE](api/sse.md) | Event envelope, ordering, terminal events, cancellation |
| [Commerce API v2](platform/commerce-api-v2.md) | The commerce search contract |
| [SDKs](sdk/README.md) | Java and TypeScript SDK behaviour and compatibility testing |
| [Web Component](sdk/web-component.md) | Framework-neutral search component |
| [Dual-SDK E2E suite](../e2e/dual-sdk/README.md) | Both SDKs against one real server |
| [Editions](editions.md) | Edition features and licences |

## Operations and security

| Document | Content |
|---|---|
| [Health](operations/health.md), [metrics](operations/metrics.md), [tracing](operations/tracing.md), [logging](operations/logging.md) | Observability |
| [Runbook](operations/runbook.md) | Operational procedures |
| [Docker deployment](deployment/docker.md) | Compose profiles |
| [Abuse protection](security/abuse-protection.md) | Rate limits and quotas |
| [Secrets handling](security/secrets-handling-policy.md) | Credentials policy |
| [Licensing](security/licensing.md), [plugin isolation](security/plugin-isolation.md), [policy packs](security/policy-packs.md) | Platform security |
| [Security audit](security/QwenBridge-1.0-Security-Audit.md) | 1.0 audit |

## Development and quality

| Document | Content |
|---|---|
| [Testing](development/testing.md) | Suites, live tests, master verification |
| [Code quality](development/code-quality.md) | Spotless, SpotBugs, architecture tests |
| [Branching and pull requests](development/branching-and-pr-policy.md) | Contribution workflow |
| [Playground](development/playground.md) | Developer playground and datasets |
| [Evaluation](evaluation/) | Quality metrics, benchmarks, capacity scenarios |

## Releases

| Document | Content |
|---|---|
| [Release process](publishing/release-process.md) | How a release is verified, versioned and published |
| [Maven Central](publishing/maven-central.md), [npm](publishing/npm.md) | Publishing targets |
| [1.0 release notes](release/QwenBridge-1.0-Release-Notes.md), [compatibility](release/QwenBridge-1.0-Compatibility.md), [decision](release/QwenBridge-1.0-Release-Decision.md) | 1.0 release state |
| [1.0 cleanup report](release/QwenBridge-1.0-Cleanup-Report.md) | QB-287: removed connectors and examples, breaking changes, measurements |
| [Follow-ups](roadmap/QwenBridge-1.0-Followups.md) | Open items after 1.0 |
| [Release history](release/README.md) | Historical V1–V9 records |

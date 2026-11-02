# Contributing to QwenBridge

Thank you for considering a contribution. QwenBridge is a quality-gated project: contributions
should be focused, tested, documented and safe to publish.

## Setup

- JDK 25, Maven 3.9+, and Node.js 22.12+ for the TypeScript SDK's development tools.
- Read [getting started](docs/development/getting-started.md),
  [local development](docs/development/local-development.md), [testing](docs/development/testing.md),
  [code quality](docs/development/code-quality.md) and the
  [branching and pull request policy](docs/development/branching-and-pr-policy.md).

## Before opening a pull request

```bash
mvn -B clean verify                          # all modules and tests
mvn -B -Pcode-quality -DskipTests verify     # Spotless, SpotBugs, architecture rules
mvn -B spotless:apply                        # format Java sources
```

For TypeScript SDK changes:

```bash
cd qwenbridge-typescript-sdk
npm ci && npm run build && npm test
npm run web:assets                           # when the Web Component bundle changes
```

For changes to the API, the SDKs or streaming, also run the end-to-end suite:

```bash
bash e2e/dual-sdk/scripts/run-dual-sdk-e2e.sh
```

## Pull request expectations

- One clearly scoped problem per pull request, with a focused description.
- Tests for changed behaviour. Never remove or weaken a test to make a build pass.
- Documentation updates for public behaviour changes, and an ADR for architectural decisions
  (`docs/adr/`).
- No secrets, credentials, `.env` files, build output (`target`, `dist`, `node_modules`) or IDE
  files.

## Architecture rules

Tests enforce these rules:
- `qwenbridge-core` stays framework-free. Its only runtime dependency is Jackson.
- The packs depend only on `qwenbridge-plugin-sdk`.
- No database, search engine or cache becomes mandatory. New data sources arrive as connector packs
  (`ConnectorPackProvider`), not as server modules.
- Quarkus is the only application framework.

If a change affects provider boundaries, public API contracts, execution flow, streaming semantics
or release behaviour, update the relevant document in `docs/architecture/` or add an ADR.

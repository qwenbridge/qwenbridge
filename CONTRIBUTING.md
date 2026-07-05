# Contributing to QwenBridge

Thank you for considering a contribution to QwenBridge.

QwenBridge is maintained as a quality-gated developer platform. Contributions should be focused, tested, documented, and safe for public release.

## Development setup

Read:

- `docs/development/local-development.md`
- `docs/development/testing.md`
- `docs/development/code-quality.md`
- `docs/development/branching-and-pr-policy.md`

## Before opening a pull request

Run:

```bash
mvn clean verify
bash scripts/verify-release.sh
```

For TypeScript SDK changes:

```bash
cd qwenbridge-typescript-sdk
npm ci
npm run build
npm test
```

## Pull request expectations

A pull request should include:

- a focused description
- tests for changed behavior
- documentation updates for public behavior changes
- no committed secrets
- no committed build output
- no `target`, `node_modules`, `dist`, or local IDE files

## Architecture changes

If a change affects architecture, provider boundaries, public API contracts, execution flow, streaming semantics, or release behavior, update the relevant documentation or add an ADR.

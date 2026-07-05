# Code Quality

QwenBridge treats code quality as a release concern, not only a local preference.

## Required baseline

Before opening a pull request:

```bash
mvn clean verify
bash scripts/verify-release.sh
```

For TypeScript changes:

```bash
cd qwenbridge-typescript-sdk
npm ci
npm test
npm run build
```

## Design rules

- Keep public API contracts explicit and backward-compatible where possible.
- Keep provider-specific code behind the AI Provider and Search Provider SPIs.
- Preserve the pipeline's typed execution context and result model.
- Add tests with every behavior change.
- Update architecture documentation and ADRs when an architectural decision changes.
- Keep generated artifacts, local build output, and dependencies out of Git.

## Static analysis and formatting

The project should enforce formatting, unused-import cleanup, bug detection, dependency scanning, and architecture rules in CI. Planned Java quality gates include Spotless, SpotBugs, dependency checks, and ArchUnit.

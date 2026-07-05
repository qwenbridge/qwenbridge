# Release Documentation Audit

This audit records the current documentation and publishing readiness state for QwenBridge.

## Current branch state

The documentation finalization branch is ahead of `main` with documentation commits.

Latest branch commits:

```text
f9d80ce docs: finalize architecture and API guides
d63b3d9 docs: expand operations and deployment guides
b27f4ea docs: refresh public README and SDK documentation
b670149 docs: add development and example guides
5f35993 docs: add release history and publishing guides
```

Current upstream base:

```text
ab5d3e8 chore: move TypeScript SDK examples to examples module (#33)
```

## Tags observed

```text
v0.1-alpha
v0.2.0
v0.6.0
v0.7.0
v0.8.0
v0.8.1
v0.9.0
v3.0.0
```

## Important release consistency findings

### V9 tag is not on the latest `main`

`v0.9.0` currently points to:

```text
43a8d55 feat(v9): complete quality and resilience verification (#31)
```

Later commits exist on `main`:

```text
55967c0 chore: add formatting and static analysis quality gates (#32)
ab5d3e8 chore: move TypeScript SDK examples to examples module (#33)
```

Before publishing public artifacts, choose one of these policies:

1. keep `v0.9.0` as the historical V9 release tag and publish a later version such as `v0.9.1`; or
2. move/recreate `v0.9.0` only if it has not been treated as a public immutable release.

For public open-source projects, prefer not moving already-pushed release tags.

### Maven versions are still snapshots

Current Maven version:

```text
0.1.0-SNAPSHOT
```

This is not publishable as a Maven Central release.

Before Maven Central publishing, set a release version such as:

```text
0.9.1
```

or:

```text
1.0.0
```

### npm version is not publishable

Current npm version:

```json
"version": "0.1.0-SNAPSHOT"
```

npm public publishing requires a valid semantic version such as:

```json
"version": "0.9.1"
```

### Maven Central metadata is incomplete

The Maven POMs need publishing metadata before Central release:

- project URL
- license metadata
- developer metadata
- SCM metadata
- source JAR generation
- Javadoc JAR generation
- artifact signing
- Central publishing plugin configuration

### License consistency

The repository license is Apache License 2.0. Documentation and package metadata should consistently refer to Apache-2.0.

### Source-distribution hygiene

The local zip inspected during documentation finalization contained local/private or generated paths such as:

- `.env`
- `.idea`
- `.git`
- `scripts/.DS_Store`
- build output and dependency directories in example modules

These must not be included in public source archives, release bundles, or committed files.

## Recommended next release strategy

Because `main` has moved beyond the existing `v0.9.0` tag, the safest public publishing version is:

```text
v0.9.1
```

Use `v0.9.1` for the documentation finalization, metadata correction, and publishing-readiness release.

## Pre-publish checklist

Before publishing Maven or npm artifacts:

```bash
git status --short
mvn clean verify
bash scripts/verify-release.sh
cd qwenbridge-typescript-sdk && npm ci && npm run build && npm test && npm pack --dry-run
```

Then verify:

- no snapshot versions in release artifacts
- no invalid npm version
- no secrets or generated dependencies committed
- release tag points to the exact commit being published
- release evidence references the correct tag

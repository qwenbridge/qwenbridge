# Publishing to npm

This guide documents the release process for the QwenBridge TypeScript SDK.

## Published package

```text
@qwenbridge/sdk
```

## Release version

The package version must be valid semantic versioning.

Valid:

```json
"version": "0.9.0"
```

Invalid for public npm publishing:

```json
"version": "0.1.0-SNAPSHOT"
```

## Pre-publish verification

```bash
cd qwenbridge-typescript-sdk
npm ci
npm run build
npm test
npm pack --dry-run
```

## Publish flow

```bash
cd qwenbridge-typescript-sdk
npm version 0.9.0 --no-git-tag-version
npm run build
npm test
npm publish --access public
```

## Package contents

The published package must include compiled JavaScript, TypeScript declarations, README, LICENSE, and NOTICE.

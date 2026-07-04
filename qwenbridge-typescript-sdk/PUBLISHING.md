# Publishing the QwenBridge TypeScript SDK

## Prerequisites

- Node.js 20+
- An npm account with publish access to the `@qwenbridge` scope
- npm CLI authenticated with `npm login`
- Clean Git working tree
- All tests passing

## Pre-publish verification

From the repository root:

~~~bash
npm --prefix qwenbridge-typescript-sdk ci
npm --prefix qwenbridge-typescript-sdk run build
npm --prefix qwenbridge-typescript-sdk test
npm --prefix qwenbridge-typescript-sdk run pack:check
~~~

Inspect the package contents carefully. The tarball should contain only:

- `dist/`
- `README.md`
- `LICENSE`
- `NOTICE`
- `package.json`

## Versioning

Use semantic versioning:

- `0.x.y` for pre-1.0 releases
- `1.0.0` for the first stable public API
- Patch releases for compatible fixes
- Minor releases for compatible features
- Major releases for breaking API changes

Before publishing, replace the snapshot version:

~~~bash
cd qwenbridge-typescript-sdk
npm version 0.1.0 --no-git-tag-version
~~~

Commit the version change and create a matching Git tag from the release commit.

## Publish

For the first public scoped package release:

~~~bash
cd qwenbridge-typescript-sdk
npm publish --access public
~~~

For later releases:

~~~bash
cd qwenbridge-typescript-sdk
npm publish
~~~

## Verify publication

~~~bash
npm view @qwenbridge/sdk version
npm view @qwenbridge/sdk dist-tags
~~~

Then test installation in a clean directory:

~~~bash
mkdir -p /tmp/qwenbridge-sdk-smoke
cd /tmp/qwenbridge-sdk-smoke
npm init -y
npm install @qwenbridge/sdk
node -e 'import("@qwenbridge/sdk").then(m => console.log(Object.keys(m)))'
~~~

## Release checklist

- Version updated
- Changelog or release notes prepared
- `npm ci` succeeds
- `npm run build` succeeds
- `npm test` succeeds
- `npm run pack:check` reviewed
- Git tag created
- npm publish completed
- npm registry version verified
- GitHub release created

# TypeScript SDK Example

The TypeScript example demonstrates synchronous search analysis and typed SSE streaming with `@qwenbridge/sdk`.

Location:

```text
examples/typescript-sdk-example
```

## Install and run

```bash
cd examples/typescript-sdk-example
npm ci
npm run sync-analyze
npm run typed-stream
```

The package is linked to the local SDK during repository development. For public consumption, replace the local dependency with the published `@qwenbridge/sdk` package version.

The example source files are:

- `src/sync-analyze.ts`
- `src/typed-stream.ts`

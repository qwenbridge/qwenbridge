# QwenBridge TypeScript SDK

Official TypeScript SDK for QwenBridge.

The SDK supports search analysis, typed errors, retry handling, raw SSE streaming, and typed SSE streaming.

## Requirements

- Node.js 20+
- TypeScript 5+
- A running QwenBridge server

## Install

After public release:

```bash
npm install @qwenbridge/sdk
```

Local monorepo development:

```bash
cd qwenbridge-typescript-sdk
npm ci
npm run build
npm test
```

## Analyze a search query

```ts
import { QwenBridgeClient } from "@qwenbridge/sdk";

const client = new QwenBridgeClient({
  baseUrl: "http://localhost:8080",
  retry: {
    maxAttempts: 3,
    initialDelayMs: 100,
    maxDelayMs: 1000,
    backoffMultiplier: 2
  }
});

const response = await client.analyze({
  requestId: "request-123",
  query: "best laptop for software development"
});

console.log(response.intent);
console.log(response.decision);
console.log(response.confidence);
```

## Typed errors

```ts
import {
  QwenBridgeApiError,
  QwenBridgeClient,
  QwenBridgeTransportError
} from "@qwenbridge/sdk";

const client = new QwenBridgeClient({
  baseUrl: "http://localhost:8080"
});

try {
  await client.analyze({ query: "iphone" });
} catch (error) {
  if (error instanceof QwenBridgeApiError) {
    console.error(error.status, error.code, error.requestId);
  } else if (error instanceof QwenBridgeTransportError) {
    console.error("Transport failure", error.message);
  } else {
    throw error;
  }
}
```

## Raw SSE stream

```ts
import { QwenBridgeStreamingClient } from "@qwenbridge/sdk";

const streamingClient = new QwenBridgeStreamingClient({
  baseUrl: "http://localhost:8080"
});

await streamingClient.stream("request-123", event => {
  console.log(event.event, event.data);
});
```

## Typed SSE stream

```ts
import { QwenBridgeStreamingClient } from "@qwenbridge/sdk";

const streamingClient = new QwenBridgeStreamingClient({
  baseUrl: "http://localhost:8080"
});

await streamingClient.streamTyped("request-123", event => {
  switch (event.payload.kind) {
    case "connected":
      console.log("Connected:", event.payload.sessionId);
      break;
    case "ai.token":
      process.stdout.write(event.payload.content);
      break;
    case "ai.completed":
      console.log("\nCompleted:", event.payload.tokenCount);
      break;
    case "ai.failed":
      console.error("AI failed:", event.payload.code, event.payload.message);
      break;
    case "unknown":
      console.log("Unknown SSE event:", event.event, event.data);
      break;
  }
});
```

## Retry behavior

The SDK retries transient failures only.

Retryable failures:

- HTTP `408`
- HTTP `429`
- HTTP `500` through `599`
- fetch transport failures

Non-retryable failures:

- validation failures
- authentication and authorization failures
- other non-transient HTTP `4xx` responses

## Development

```bash
npm ci
npm run build
npm test
```

## Examples

The repository's independent TypeScript consumer,
[`e2e/dual-sdk/typescript-consumer`](../e2e/dual-sdk/typescript-consumer), installs this package
from its packed tarball and imports it by name only. It covers:
- v2 search;
- streaming and `AbortController` cancellation;
- typed errors;
- the v1 analyze client;
- typed v1 streaming.

The repository README has a short quick start.

## Publishing

Publishing documentation is available in:

```text
PUBLISHING.md
docs/publishing/npm.md
```

### Persistent commerce and Web Component

`CommerceClient` also exposes `createSession`, `session`, `turn`, `streamTurn`,
`proposals` and `accept`. Pass the current session revision on turns/acceptance.
No condition changes until the owner explicitly accepts an alternative.

```javascript
import '@qwenbridge/sdk/web-component';
const element = document.querySelector('qwenbridge-search');
element.configure({ baseUrl: 'https://your-search-api.example', transport: 'sse' });
```

Add `<qwenbridge-search></qwenbridge-search>` to the page. Set a bearer token from
trusted session code when authentication is enabled; credentials stay in memory.
The component includes RTL, keyboard access, results, clarifications and errors.
See [Table 1 contracts](../docs/platform/table-1-api.md) for backend selection,
locked conditions, signed packs, licensing and the Developer Playground.

### v2 client: errors, timeouts, retries and cancellation (1.0)

```typescript
import { CommerceClient, COMMERCE_CONTRACT_V1, QwenBridgeApiError, RetryPolicy } from "@qwenbridge/sdk";

const client = new CommerceClient({
  baseUrl: "https://search.example.com", bearerToken: apiKey,
  timeoutMs: 30_000,                        // per call; also sent as X-Request-Timeout-Ms
  retry: new RetryPolicy({ maxAttempts: 3 }) // safe operations only
});
await client.requireCompatibleServer();
const controller = new AbortController();
const pending = client.search({ query: "black shoes under 100 EUR", contract: COMMERCE_CONTRACT_V1 }, controller.signal);
controller.abort();                          // aborts the call and asks the server to cancel
try { await pending; } catch (e) { if (e instanceof QwenBridgeApiError) console.log(e.code, e.category, e.retryable); }
```

The client depends neither on the Web Component nor on any framework, and runs on Node.js 20+ and
in browsers. See [docs/sdk](../docs/sdk/README.md) for the full behaviour and compatibility tests.

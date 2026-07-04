# QwenBridge TypeScript SDK

Official TypeScript SDK for QwenBridge.

## Requirements

- Node.js 20+
- TypeScript 5+
- A running QwenBridge server

## Install

Local monorepo development:

~~~bash
npm install
npm run build
npm test
~~~

Package usage after publishing:

~~~bash
npm install @qwenbridge/sdk
~~~

## Analyze search query

~~~ts
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
~~~

## Typed errors

~~~ts
import {
  QwenBridgeApiError,
  QwenBridgeClient,
  QwenBridgeTransportError
} from "@qwenbridge/sdk";

const client = new QwenBridgeClient({
  baseUrl: "http://localhost:8080"
});

try {
  await client.analyze({
    query: "iphone"
  });
} catch (error) {
  if (error instanceof QwenBridgeApiError) {
    console.error(error.status, error.code, error.requestId);
  } else if (error instanceof QwenBridgeTransportError) {
    console.error("Transport failure", error.message);
  } else {
    throw error;
  }
}
~~~

## Raw SSE stream

~~~ts
import { QwenBridgeStreamingClient } from "@qwenbridge/sdk";

const streamingClient = new QwenBridgeStreamingClient({
  baseUrl: "http://localhost:8080"
});

await streamingClient.stream("request-123", event => {
  console.log(event.event, event.data);
});
~~~

## Typed SSE stream

~~~ts
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
      console.error(
        "AI failed:",
        event.payload.code,
        event.payload.message
      );
      break;
    case "unknown":
      console.log("Unknown SSE event:", event.event, event.data);
      break;
  }
});
~~~

## Retry behavior

The SDK retries transient failures only.

Retryable failures:

- HTTP `408`
- HTTP `429`
- HTTP `500` through `599`
- Fetch transport failures

Non-retryable failures:

- Validation failures
- Authentication and authorization failures
- Other non-transient HTTP `4xx` responses

## Development

~~~bash
npm install
npm run build
npm test
~~~

The examples are source examples intended to document SDK usage. They require a running QwenBridge server.

~~~bash
QWENBRIDGE_BASE_URL=http://localhost:8080 \
node --experimental-strip-types examples/sync-analyze.ts
~~~

~~~bash
QWENBRIDGE_BASE_URL=http://localhost:8080 \
node --experimental-strip-types examples/typed-stream.ts
~~~

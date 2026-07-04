import { QwenBridgeStreamingClient } from "../src/index.js";

const client = new QwenBridgeStreamingClient({
  baseUrl: process.env.QWENBRIDGE_BASE_URL ?? "http://localhost:8080"
});

await client.streamTyped("ts-stream-example", event => {
  switch (event.payload.kind) {
    case "connected":
      console.log("connected", event.payload.sessionId);
      break;
    case "ai.token":
      process.stdout.write(event.payload.content);
      break;
    case "ai.completed":
      console.log("\ncompleted", event.payload.tokenCount);
      break;
    case "ai.failed":
      console.error("failed", event.payload.code, event.payload.message);
      break;
    default:
      console.log("unknown event", event.event, event.data);
  }
});

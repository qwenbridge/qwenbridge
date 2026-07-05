import { QwenBridgeStreamingClient } from "@qwenbridge/sdk";

const client = new QwenBridgeStreamingClient({
  baseUrl: process.env.QWENBRIDGE_BASE_URL ?? "http://localhost:8080"
});

await client.streamTyped("ts-stream-example", event => {
  const payload = event.payload;

  switch (payload.kind) {
    case "connected":
      if ("sessionId" in payload) {
        console.log("connected", payload.sessionId);
      }
      break;

    case "ai.token":
      if ("content" in payload) {
        process.stdout.write(payload.content);
      }
      break;

    case "ai.completed":
      if ("tokenCount" in payload) {
        console.log("\ncompleted", payload.tokenCount);
      }
      break;

    case "ai.failed":
      if ("code" in payload && "message" in payload) {
        console.error("failed", payload.code, payload.message);
      }
      break;

    default:
      console.log("event", event.event, event.data);
  }
});

import { QwenBridgeClient } from "@qwenbridge/sdk";

const client = new QwenBridgeClient({
  baseUrl: process.env.QWENBRIDGE_BASE_URL ?? "http://localhost:8080"
});

const response = await client.analyze({
  requestId: "ts-sync-example",
  query: "best laptop for software development"
});

console.log(response);

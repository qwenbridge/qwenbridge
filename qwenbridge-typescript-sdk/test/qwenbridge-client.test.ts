import { describe, expect, it } from "vitest";
import { QwenBridgeClient } from "../src/index.js";

describe("QwenBridgeClient", () => {
  it("exposes the configured base URL", () => {
    const client = new QwenBridgeClient({
      baseUrl: "http://localhost:8080"
    });

    expect(client.baseUrl).toBe("http://localhost:8080");
  });

  it("fails explicitly until analyze transport is implemented", async () => {
    const client = new QwenBridgeClient({
      baseUrl: "http://localhost:8080"
    });

    await expect(
      client.analyze({
        query: "What is QwenBridge?"
      })
    ).rejects.toThrow(
      "QwenBridgeClient.analyze is not implemented yet."
    );
  });
});

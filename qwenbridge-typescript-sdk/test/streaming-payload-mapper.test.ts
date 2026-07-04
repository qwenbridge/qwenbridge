import { describe, expect, it } from "vitest";
import { StreamingPayloadMapper } from "../src/index.js";

describe("StreamingPayloadMapper", () => {
  const mapper = new StreamingPayloadMapper();

  it("maps stream.connected payload", () => {
    const payload = mapper.map({
      event: "stream.connected",
      data: JSON.stringify({
        requestId: "req-1",
        sessionId: "session-1"
      })
    });

    expect(payload).toEqual({
      kind: "connected",
      requestId: "req-1",
      sessionId: "session-1"
    });
  });

  it("maps ai.token payload", () => {
    const payload = mapper.map({
      event: "ai.token",
      data: JSON.stringify({
        requestId: "req-1",
        tokenIndex: 7,
        content: "hello",
        terminal: false
      })
    });

    expect(payload).toEqual({
      kind: "ai.token",
      requestId: "req-1",
      tokenIndex: 7,
      content: "hello",
      terminal: false
    });
  });

  it("maps ai.completed payload", () => {
    const payload = mapper.map({
      event: "ai.completed",
      data: JSON.stringify({
        requestId: "req-1",
        tokenCount: 12,
        terminal: true
      })
    });

    expect(payload).toEqual({
      kind: "ai.completed",
      requestId: "req-1",
      tokenCount: 12,
      terminal: true
    });
  });

  it("maps ai.failed payload", () => {
    const payload = mapper.map({
      event: "ai.failed",
      data: JSON.stringify({
        requestId: "req-1",
        code: "AI_PROVIDER_ERROR",
        message: "provider failed",
        terminal: true
      })
    });

    expect(payload).toEqual({
      kind: "ai.failed",
      requestId: "req-1",
      code: "AI_PROVIDER_ERROR",
      message: "provider failed",
      terminal: true
    });
  });

  it("maps unknown event payload", () => {
    const payload = mapper.map({
      event: "custom.event",
      data: "{\"hello\":\"world\"}"
    });

    expect(payload).toEqual({
      kind: "unknown",
      event: "custom.event",
      raw: "{\"hello\":\"world\"}",
      parsed: {
        hello: "world"
      }
    });
  });

  it("keeps invalid JSON as unknown raw payload", () => {
    const payload = mapper.map({
      event: "custom.event",
      data: "not-json"
    });

    expect(payload).toEqual({
      kind: "unknown",
      event: "custom.event",
      raw: "not-json",
      parsed: undefined
    });
  });
});

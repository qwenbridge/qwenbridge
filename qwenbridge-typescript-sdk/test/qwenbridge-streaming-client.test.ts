import { describe, expect, it, vi } from "vitest";
import {
  QwenBridgeStreamingClient,
  QwenBridgeTransportError,
  type StreamingEvent
} from "../src/index.js";

describe("QwenBridgeStreamingClient", () => {
  it("opens SSE stream and parses raw events", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        [
          "event: stream.connected",
          "data: {\"requestId\":\"req-123\"}",
          "",
          "event: ai.token",
          "data: {\"content\":\"hello\"}",
          "",
          "event: ai.completed",
          "data: {\"terminal\":true}",
          ""
        ].join("\n"),
        {
          status: 200,
          headers: {
            "Content-Type": "text/event-stream"
          }
        }
      )
    );

    const client = new QwenBridgeStreamingClient({
      baseUrl: "http://localhost:8080",
      fetch: fetchMock
    });

    const events: StreamingEvent[] = [];

    await client.stream("req-123", event => {
      events.push(event);
    });

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/search/stream/req-123",
      {
        method: "GET",
        headers: {
          "Accept": "text/event-stream"
        }
      }
    );

    expect(events).toEqual([
      {
        event: "stream.connected",
        data: "{\"requestId\":\"req-123\"}"
      },
      {
        event: "ai.token",
        data: "{\"content\":\"hello\"}"
      },
      {
        event: "ai.completed",
        data: "{\"terminal\":true}"
      }
    ]);
  });

  it("uses message as default event name", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response("data: plain-message\n\n", {
        status: 200,
        headers: {
          "Content-Type": "text/event-stream"
        }
      })
    );

    const client = new QwenBridgeStreamingClient({
      baseUrl: "http://localhost:8080",
      fetch: fetchMock
    });

    const events: StreamingEvent[] = [];

    await client.stream("req-456", event => {
      events.push(event);
    });

    expect(events).toEqual([
      {
        event: "message",
        data: "plain-message"
      }
    ]);
  });

  it("parses typed SSE payloads", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        [
          "event: stream.connected",
          "data: {\"requestId\":\"req-typed\",\"sessionId\":\"session-typed\"}",
          "",
          "event: ai.token",
          "data: {\"requestId\":\"req-typed\",\"tokenIndex\":1,\"content\":\"hello\",\"terminal\":false}",
          "",
          "event: ai.completed",
          "data: {\"requestId\":\"req-typed\",\"tokenCount\":1,\"terminal\":true}",
          ""
        ].join("\n"),
        {
          status: 200,
          headers: {
            "Content-Type": "text/event-stream"
          }
        }
      )
    );

    const client = new QwenBridgeStreamingClient({
      baseUrl: "http://localhost:8080",
      fetch: fetchMock
    });

    const payloadKinds: string[] = [];

    await client.streamTyped("req-typed", event => {
      payloadKinds.push(event.payload.kind);
    });

    expect(payloadKinds).toEqual([
      "connected",
      "ai.token",
      "ai.completed"
    ]);
  });

  it("rejects blank request id before HTTP call", async () => {
    const fetchMock = vi.fn<typeof fetch>();

    const client = new QwenBridgeStreamingClient({
      baseUrl: "http://localhost:8080",
      fetch: fetchMock
    });

    await expect(
      client.stream(" ", () => undefined)
    ).rejects.toThrow("requestId must not be blank");

    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("maps non-success stream response to transport error", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response("not found", {
        status: 404
      })
    );

    const client = new QwenBridgeStreamingClient({
      baseUrl: "http://localhost:8080",
      fetch: fetchMock
    });

    await expect(
      client.stream("missing", () => undefined)
    ).rejects.toBeInstanceOf(QwenBridgeTransportError);
  });

  it("wraps fetch failure as transport error", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockRejectedValue(
      new Error("connection refused")
    );

    const client = new QwenBridgeStreamingClient({
      baseUrl: "http://localhost:8080",
      fetch: fetchMock
    });

    await expect(
      client.stream("req-789", () => undefined)
    ).rejects.toBeInstanceOf(QwenBridgeTransportError);
  });
});

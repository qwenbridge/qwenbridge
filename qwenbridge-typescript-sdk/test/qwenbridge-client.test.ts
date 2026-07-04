import { describe, expect, it, vi } from "vitest";
import {
  QwenBridgeApiError,
  QwenBridgeClient,
  QwenBridgeTransportError
} from "../src/index.js";

describe("QwenBridgeClient", () => {
  it("exposes the configured base URL", () => {
    const client = new QwenBridgeClient({
      baseUrl: "http://localhost:8080"
    });

    expect(client.baseUrl).toBe("http://localhost:8080");
  });

  it("posts analyze request and maps successful response", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      jsonResponse(200, {
        requestId: "req-123",
        processingTimeMs: 42,
        originalQuery: "iphone",
        language: "en",
        intent: "PRODUCT_SEARCH",
        decision: "SEARCH",
        confidence: 0.91,
        rewrites: ["iphone"],
        threatReasons: [],
        semanticValidated: true,
        semanticScore: 0.88,
        policyPassed: true,
        policyViolations: [],
        executionPlan: {},
        executionResult: {},
        search: {},
        cache: {},
        pipelineTrace: []
      })
    );

    const client = new QwenBridgeClient({
      baseUrl: "http://localhost:8080",
      fetch: fetchMock
    });

    const response = await client.analyze({
      requestId: "req-123",
      query: "iphone"
    });

    expect(response.requestId).toBe("req-123");
    expect(response.originalQuery).toBe("iphone");
    expect(response.intent).toBe("PRODUCT_SEARCH");
    expect(response.decision).toBe("SEARCH");
    expect(response.confidence).toBe(0.91);

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/search/analyze",
      expect.objectContaining({
        method: "POST",
        headers: {
          "Accept": "application/json",
          "Content-Type": "application/json",
          "X-Request-Id": "req-123"
        },
        body: JSON.stringify({
          requestId: "req-123",
          query: "iphone"
        })
      })
    );
  });

  it("does not send request id header when request id is blank", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      jsonResponse(200, {
        requestId: "generated",
        originalQuery: "laptop"
      })
    );

    const client = new QwenBridgeClient({
      baseUrl: "http://localhost:8080",
      fetch: fetchMock
    });

    await client.analyze({
      requestId: " ",
      query: "laptop"
    });

    const [, init] = fetchMock.mock.calls[0];
    expect((init?.headers as Record<string, string>)["X-Request-Id"]).toBeUndefined();
  });

  it("maps API error response", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      jsonResponse(400, {
        timestamp: "2026-07-04T13:00:00Z",
        status: 400,
        error: "Bad Request",
        code: "VALIDATION_ERROR",
        message: "query must not be blank",
        path: "/api/v1/search/analyze",
        requestId: "req-error"
      })
    );

    const client = new QwenBridgeClient({
      baseUrl: "http://localhost:8080",
      fetch: fetchMock
    });

    try {
      await client.analyze({
        requestId: "req-error",
        query: "iphone"
      });

      throw new Error("Expected QwenBridgeApiError");
    } catch (caught) {
      expect(caught).toBeInstanceOf(QwenBridgeApiError);

      const apiError = caught as QwenBridgeApiError;
      expect(apiError.status).toBe(400);
      expect(apiError.code).toBe("VALIDATION_ERROR");
      expect(apiError.requestId).toBe("req-error");
      expect(apiError.message).toBe("query must not be blank");
    }
  });

  it("falls back when API error body is not JSON", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response("Service unavailable", {
        status: 503,
        statusText: "Service Unavailable"
      })
    );

    const client = new QwenBridgeClient({
      baseUrl: "http://localhost:8080",
      fetch: fetchMock
    });

    try {
      await client.analyze({ query: "iphone" });
    } catch (caught) {
      const apiError = caught as QwenBridgeApiError;
      expect(apiError.status).toBe(503);
      expect(apiError.message).toBe("QwenBridge API returned HTTP 503");
    }
  });

  it("wraps fetch failures as transport errors", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockRejectedValue(
      new Error("connection refused")
    );

    const client = new QwenBridgeClient({
      baseUrl: "http://localhost:8080",
      fetch: fetchMock
    });

    await expect(
      client.analyze({ query: "iphone" })
    ).rejects.toBeInstanceOf(QwenBridgeTransportError);
  });

  it("retries transient API failure before returning success", async () => {
    const fetchMock = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse(503, {
        status: 503,
        message: "temporarily unavailable"
      }))
      .mockResolvedValueOnce(jsonResponse(200, {
        requestId: "req-retry",
        originalQuery: "iphone"
      }));

    const client = new QwenBridgeClient({
      baseUrl: "http://localhost:8080",
      fetch: fetchMock,
      retry: {
        maxAttempts: 2,
        initialDelayMs: 0
      }
    });

    const response = await client.analyze({
      requestId: "req-retry",
      query: "iphone"
    });

    expect(response.requestId).toBe("req-retry");
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("rejects blank query before HTTP call", async () => {
    const fetchMock = vi.fn<typeof fetch>();

    const client = new QwenBridgeClient({
      baseUrl: "http://localhost:8080",
      fetch: fetchMock
    });

    await expect(
      client.analyze({ query: " " })
    ).rejects.toThrow("query must not be blank");

    expect(fetchMock).not.toHaveBeenCalled();
  });
});

function jsonResponse(
  status: number,
  body: unknown
): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json"
    }
  });
}

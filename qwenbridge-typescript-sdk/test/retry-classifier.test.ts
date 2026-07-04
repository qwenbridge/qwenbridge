import { describe, expect, it } from "vitest";
import {
  QwenBridgeApiError,
  QwenBridgeTransportError,
  RetryClassifier
} from "../src/index.js";

describe("RetryClassifier", () => {
  const classifier = new RetryClassifier();

  it("retries transport errors", () => {
    expect(classifier.isRetryable(
      new QwenBridgeTransportError("connection refused")
    )).toBe(true);
  });

  it("retries transient API statuses", () => {
    expect(classifier.isRetryable(apiError(408))).toBe(true);
    expect(classifier.isRetryable(apiError(429))).toBe(true);
    expect(classifier.isRetryable(apiError(500))).toBe(true);
    expect(classifier.isRetryable(apiError(503))).toBe(true);
  });

  it("does not retry permanent API statuses", () => {
    expect(classifier.isRetryable(apiError(400))).toBe(false);
    expect(classifier.isRetryable(apiError(401))).toBe(false);
    expect(classifier.isRetryable(apiError(403))).toBe(false);
    expect(classifier.isRetryable(apiError(404))).toBe(false);
    expect(classifier.isRetryable(apiError(422))).toBe(false);
  });

  it("does not retry unknown errors", () => {
    expect(classifier.isRetryable(new Error("programming error"))).toBe(false);
  });
});

function apiError(status: number): QwenBridgeApiError {
  return new QwenBridgeApiError({
    status,
    message: "HTTP " + status
  });
}

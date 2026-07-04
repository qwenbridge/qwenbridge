import { describe, expect, it } from "vitest";
import { RetryPolicy } from "../src/index.js";

describe("RetryPolicy", () => {
  it("uses production defaults", () => {
    const policy = new RetryPolicy();

    expect(policy.maxAttempts).toBe(3);
    expect(policy.initialDelayMs).toBe(100);
    expect(policy.maxDelayMs).toBe(1_000);
    expect(policy.backoffMultiplier).toBe(2);
  });

  it("calculates capped exponential backoff", () => {
    const policy = new RetryPolicy({
      initialDelayMs: 100,
      maxDelayMs: 250,
      backoffMultiplier: 2
    });

    expect(policy.delayBeforeAttempt(1)).toBe(0);
    expect(policy.delayBeforeAttempt(2)).toBe(100);
    expect(policy.delayBeforeAttempt(3)).toBe(200);
    expect(policy.delayBeforeAttempt(4)).toBe(250);
  });

  it("rejects invalid configuration", () => {
    expect(() => new RetryPolicy({ maxAttempts: 0 }))
      .toThrow("maxAttempts must be an integer greater than or equal to 1");

    expect(() => new RetryPolicy({ initialDelayMs: -1 }))
      .toThrow("initialDelayMs must be greater than or equal to 0");

    expect(() => new RetryPolicy({
      initialDelayMs: 200,
      maxDelayMs: 100
    })).toThrow("maxDelayMs must be greater than or equal to initialDelayMs");

    expect(() => new RetryPolicy({ backoffMultiplier: 0.5 }))
      .toThrow("backoffMultiplier must be greater than or equal to 1");
  });
});

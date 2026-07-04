import { describe, expect, it, vi } from "vitest";
import {
  QwenBridgeApiError,
  QwenBridgeTransportError,
  RetryExecutor,
  RetryPolicy,
  type Sleeper
} from "../src/index.js";

describe("RetryExecutor", () => {
  it("returns the first successful result without sleeping", async () => {
    const operation = vi.fn().mockResolvedValue("success");
    const sleeper = vi.fn<Sleeper>().mockResolvedValue(undefined);

    const executor = new RetryExecutor(
      new RetryPolicy(),
      undefined,
      sleeper
    );

    await expect(executor.execute(operation)).resolves.toBe("success");
    expect(operation).toHaveBeenCalledTimes(1);
    expect(sleeper).not.toHaveBeenCalled();
  });

  it("retries transient failure and returns later success", async () => {
    const operation = vi.fn()
      .mockRejectedValueOnce(new QwenBridgeTransportError("timeout"))
      .mockResolvedValueOnce("success");

    const sleeper = vi.fn<Sleeper>().mockResolvedValue(undefined);

    const executor = new RetryExecutor(
      new RetryPolicy({
        maxAttempts: 3,
        initialDelayMs: 25
      }),
      undefined,
      sleeper
    );

    await expect(executor.execute(operation)).resolves.toBe("success");
    expect(operation).toHaveBeenCalledTimes(2);
    expect(sleeper).toHaveBeenCalledWith(25);
  });

  it("stops after max attempts for retryable failure", async () => {
    const failure = new QwenBridgeApiError({
      status: 503,
      message: "unavailable"
    });

    const operation = vi.fn().mockRejectedValue(failure);
    const sleeper = vi.fn<Sleeper>().mockResolvedValue(undefined);

    const executor = new RetryExecutor(
      new RetryPolicy({
        maxAttempts: 3,
        initialDelayMs: 10
      }),
      undefined,
      sleeper
    );

    await expect(executor.execute(operation)).rejects.toBe(failure);
    expect(operation).toHaveBeenCalledTimes(3);
    expect(sleeper).toHaveBeenNthCalledWith(1, 10);
    expect(sleeper).toHaveBeenNthCalledWith(2, 20);
  });

  it("does not retry permanent API failure", async () => {
    const failure = new QwenBridgeApiError({
      status: 400,
      message: "bad request"
    });

    const operation = vi.fn().mockRejectedValue(failure);
    const sleeper = vi.fn<Sleeper>().mockResolvedValue(undefined);

    const executor = new RetryExecutor(
      new RetryPolicy(),
      undefined,
      sleeper
    );

    await expect(executor.execute(operation)).rejects.toBe(failure);
    expect(operation).toHaveBeenCalledTimes(1);
    expect(sleeper).not.toHaveBeenCalled();
  });
});

export interface RetryPolicyOptions {
  maxAttempts?: number;
  initialDelayMs?: number;
  maxDelayMs?: number;
  backoffMultiplier?: number;
}

export class RetryPolicy {
  public readonly maxAttempts: number;
  public readonly initialDelayMs: number;
  public readonly maxDelayMs: number;
  public readonly backoffMultiplier: number;

  public constructor(options: RetryPolicyOptions = {}) {
    this.maxAttempts = options.maxAttempts ?? 3;
    this.initialDelayMs = options.initialDelayMs ?? 100;
    this.maxDelayMs = options.maxDelayMs ?? 1_000;
    this.backoffMultiplier = options.backoffMultiplier ?? 2;

    if (!Number.isInteger(this.maxAttempts) || this.maxAttempts < 1) {
      throw new TypeError("maxAttempts must be an integer greater than or equal to 1");
    }

    if (!Number.isFinite(this.initialDelayMs) || this.initialDelayMs < 0) {
      throw new TypeError("initialDelayMs must be greater than or equal to 0");
    }

    if (!Number.isFinite(this.maxDelayMs) || this.maxDelayMs < this.initialDelayMs) {
      throw new TypeError("maxDelayMs must be greater than or equal to initialDelayMs");
    }

    if (!Number.isFinite(this.backoffMultiplier) || this.backoffMultiplier < 1) {
      throw new TypeError("backoffMultiplier must be greater than or equal to 1");
    }
  }

  public delayBeforeAttempt(attempt: number): number {
    if (!Number.isInteger(attempt) || attempt < 2) {
      return 0;
    }

    const retryNumber = attempt - 2;
    const calculatedDelay =
      this.initialDelayMs * Math.pow(this.backoffMultiplier, retryNumber);

    return Math.min(Math.round(calculatedDelay), this.maxDelayMs);
  }
}

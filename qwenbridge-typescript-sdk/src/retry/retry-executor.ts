import type { Sleeper } from "./sleeper.js";
import { defaultSleeper } from "./sleeper.js";
import { RetryClassifier } from "./retry-classifier.js";
import { RetryPolicy } from "./retry-policy.js";

export class RetryExecutor {
  public constructor(
    private readonly policy: RetryPolicy,
    private readonly classifier: RetryClassifier = new RetryClassifier(),
    private readonly sleeper: Sleeper = defaultSleeper
  ) {}

  public async execute<T>(operation: () => Promise<T>): Promise<T> {
    let lastError: unknown;

    for (let attempt = 1; attempt <= this.policy.maxAttempts; attempt += 1) {
      try {
        return await operation();
      } catch (error) {
        lastError = error;

        const shouldRetry =
          attempt < this.policy.maxAttempts
          && this.classifier.isRetryable(error);

        if (!shouldRetry) {
          throw error;
        }

        const delayMs = this.policy.delayBeforeAttempt(attempt + 1);

        if (delayMs > 0) {
          await this.sleeper(delayMs);
        }
      }
    }

    throw lastError;
  }
}

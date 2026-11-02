import { QwenBridgeApiError, QwenBridgeTransportError } from "../errors.js";

/**
 * Which failures are worth another attempt (QB-190): transport failures, and server errors the
 * server marks `retryable` (v2) or, without that flag, 408, 429 and 5xx. Whether an operation may
 * be retried at all (it must be safe or idempotent) is the caller's decision.
 */
export class RetryClassifier {
  public isRetryable(error: unknown): boolean {
    if (error instanceof QwenBridgeTransportError) {
      return true;
    }

    if (error instanceof QwenBridgeApiError) {
      if (error.retryable !== undefined) {
        return error.retryable;
      }
      return error.status === 408
        || error.status === 429
        || error.status >= 500;
    }

    return false;
  }
}

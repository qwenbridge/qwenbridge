import { QwenBridgeApiError, QwenBridgeTransportError } from "../errors.js";

export class RetryClassifier {
  public isRetryable(error: unknown): boolean {
    if (error instanceof QwenBridgeTransportError) {
      return true;
    }

    if (error instanceof QwenBridgeApiError) {
      return error.status === 408
        || error.status === 429
        || error.status >= 500;
    }

    return false;
  }
}

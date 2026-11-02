package io.qwenbridge.sdk.retry;

import io.qwenbridge.sdk.exception.QwenBridgeApiException;
import io.qwenbridge.sdk.exception.QwenBridgeTransportException;
import java.util.Set;

/**
 * Which failures are worth another attempt (QB-190). Transport failures are; server errors are when
 * the server says so ({@code retryable} on v2) or, for v1, when the status is 429, 502, 503 or 504.
 * Whether an operation may be retried at all (it must be safe or idempotent) is the caller's call.
 */
public final class RetryClassifier {

  private static final Set<Integer> RETRYABLE_HTTP_STATUSES = Set.of(429, 502, 503, 504);

  private RetryClassifier() {}

  public static boolean isRetryable(Throwable throwable) {
    if (throwable instanceof QwenBridgeTransportException) {
      return true;
    }
    if (throwable instanceof QwenBridgeApiException apiException
        && apiException.apiError() != null) {
      Boolean declared = apiException.apiError().retryable();
      return declared != null
          ? declared
          : RETRYABLE_HTTP_STATUSES.contains(apiException.apiError().status());
    }
    return false;
  }
}

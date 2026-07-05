package io.qwenbridge.sdk.retry;

import io.qwenbridge.sdk.exception.QwenBridgeApiException;
import io.qwenbridge.sdk.exception.QwenBridgeTransportException;
import java.util.Set;

public final class RetryClassifier {

  private static final Set<Integer> RETRYABLE_HTTP_STATUSES = Set.of(429, 502, 503, 504);

  private RetryClassifier() {}

  public static boolean isRetryable(Throwable throwable) {
    if (throwable instanceof QwenBridgeTransportException) {
      return true;
    }

    if (throwable instanceof QwenBridgeApiException apiException
        && apiException.apiError() != null) {
      return RETRYABLE_HTTP_STATUSES.contains(apiException.apiError().status());
    }

    return false;
  }
}

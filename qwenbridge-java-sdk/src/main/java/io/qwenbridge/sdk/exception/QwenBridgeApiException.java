package io.qwenbridge.sdk.exception;

/** The server answered with an error (QB-186): status, stable code, category and retryability. */
public class QwenBridgeApiException extends RuntimeException {

  private final QwenBridgeApiError apiError;

  public QwenBridgeApiException(QwenBridgeApiError apiError) {
    super(apiError == null ? "QwenBridge API error" : apiError.message());
    this.apiError = apiError;
  }

  public QwenBridgeApiError apiError() {
    return apiError;
  }

  public int status() {
    return apiError == null ? 0 : apiError.status();
  }

  /** The stable error code (for example {@code TIMEOUT}), or null when the body had none. */
  public String code() {
    return apiError == null ? null : apiError.code();
  }

  /** The v2 error category (for example {@code DEPENDENCY}), or null for v1 responses. */
  public String category() {
    return apiError == null ? null : apiError.category();
  }
}

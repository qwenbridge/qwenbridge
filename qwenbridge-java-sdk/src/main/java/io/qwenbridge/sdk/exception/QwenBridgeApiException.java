package io.qwenbridge.sdk.exception;

public class QwenBridgeApiException extends RuntimeException {

  private final QwenBridgeApiError apiError;

  public QwenBridgeApiException(QwenBridgeApiError apiError) {
    super(apiError == null ? "QwenBridge API error" : apiError.message());
    this.apiError = apiError;
  }

  public QwenBridgeApiError apiError() {
    return apiError;
  }
}

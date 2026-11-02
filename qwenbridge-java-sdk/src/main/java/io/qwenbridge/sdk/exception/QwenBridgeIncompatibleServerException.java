package io.qwenbridge.sdk.exception;

/**
 * The server speaks a contract version this SDK does not support (QB-189). Raised instead of
 * silently mapping an incompatible response.
 */
public class QwenBridgeIncompatibleServerException extends RuntimeException {

  public QwenBridgeIncompatibleServerException(String message) {
    super(message);
  }
}

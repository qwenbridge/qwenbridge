package io.qwenbridge.sdk.exception;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * The server's error body (QB-181, QB-186). The first seven fields are the v1 contract; {@code
 * category} and {@code retryable} are sent by {@code /api/v2} endpoints and are null for v1.
 * Unknown fields from newer servers are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QwenBridgeApiError(
    Instant timestamp,
    int status,
    String error,
    String code,
    String message,
    String path,
    String requestId,
    String category,
    Boolean retryable) {

  /** The 0.9 (v1) shape. */
  public QwenBridgeApiError(
      Instant timestamp,
      int status,
      String error,
      String code,
      String message,
      String path,
      String requestId) {
    this(timestamp, status, error, code, message, path, requestId, null, null);
  }

  /** An error for a response that carried no parseable error body. */
  public static QwenBridgeApiError ofStatus(int status, String path) {
    return new QwenBridgeApiError(
        null, status, null, null, "HTTP " + status, path, null, null, null);
  }
}

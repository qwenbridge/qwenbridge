package io.qwenbridge.exception;

/**
 * The family of an API error (QB-181), stable across releases. Clients branch on the category (and
 * on {@code retryable}) rather than on HTTP status or message text.
 */
public enum ErrorCategory {
  VALIDATION(false),
  AUTHENTICATION(false),
  AUTHORIZATION(false),
  NOT_FOUND(false),
  CONFLICT(false),
  RATE_LIMIT(true),
  UNSUPPORTED(false),
  TIMEOUT(true),
  CANCELLED(false),
  DEPENDENCY(true),
  INTERNAL(false);

  private final boolean retryable;

  ErrorCategory(boolean retryable) {
    this.retryable = retryable;
  }

  /** Whether repeating the same request later may succeed. */
  public boolean retryable() {
    return retryable;
  }
}

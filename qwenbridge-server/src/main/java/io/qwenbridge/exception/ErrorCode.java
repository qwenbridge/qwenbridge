package io.qwenbridge.exception;

/** Stable public error codes (QB-181). Codes are only ever added, never renamed or removed. */
public enum ErrorCode {
  VALIDATION_ERROR(ErrorCategory.VALIDATION),
  BAD_REQUEST(ErrorCategory.VALIDATION),
  UNAUTHORIZED(ErrorCategory.AUTHENTICATION),
  FORBIDDEN(ErrorCategory.AUTHORIZATION),
  NOT_FOUND(ErrorCategory.NOT_FOUND),
  CONFLICT(ErrorCategory.CONFLICT),
  RATE_LIMITED(ErrorCategory.RATE_LIMIT),
  AI_PROVIDER_ERROR(ErrorCategory.DEPENDENCY),
  SEARCH_PROVIDER_ERROR(ErrorCategory.DEPENDENCY),
  TIMEOUT(ErrorCategory.TIMEOUT),
  INTERNAL_ERROR(ErrorCategory.INTERNAL),
  /** v2: the caller cancelled the request before it finished. */
  CANCELLED(ErrorCategory.CANCELLED),
  /** v2: the requested capability is not supported by this deployment or connector. */
  UNSUPPORTED_CAPABILITY(ErrorCategory.UNSUPPORTED),
  /** v2: a backend, connector or other dependency is unavailable. */
  DEPENDENCY_UNAVAILABLE(ErrorCategory.DEPENDENCY),
  /** An authenticated deployment refused a tenant-less v1 endpoint. */
  TENANT_API_REQUIRED(ErrorCategory.AUTHORIZATION);

  private final ErrorCategory category;

  ErrorCode(ErrorCategory category) {
    this.category = category;
  }

  public ErrorCategory category() {
    return category;
  }
}

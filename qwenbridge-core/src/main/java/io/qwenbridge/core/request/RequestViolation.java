package io.qwenbridge.core.request;

import java.util.Objects;

/**
 * One reason a request was rejected. Messages describe the rule that failed and never echo the
 * offending value, so violations are safe to return to clients and to log.
 */
public record RequestViolation(String field, Code code, String message) {

  public enum Code {
    REQUIRED,
    BLANK,
    TOO_LONG,
    TOO_MANY,
    INVALID_FORMAT,
    INVALID_LANGUAGE_TAG,
    SENSITIVE_CONTEXT_KEY,
    UNSUPPORTED_VERSION
  }

  public RequestViolation {
    Objects.requireNonNull(field, "field must not be null");
    Objects.requireNonNull(code, "code must not be null");
    Objects.requireNonNull(message, "message must not be null");
  }

  @Override
  public String toString() {
    return field + " " + message;
  }
}

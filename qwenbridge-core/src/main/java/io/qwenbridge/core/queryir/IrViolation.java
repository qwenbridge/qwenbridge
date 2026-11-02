package io.qwenbridge.core.queryir;

import java.util.Objects;

/**
 * A typed reason a QueryIR document or object is invalid (QB-041). {@code path} is a JSON-pointer
 * style location such as {@code /constraints/2/value}. Messages describe the rule and never echo
 * user text.
 */
public record IrViolation(String path, Code code, String message) {

  public enum Code {
    MALFORMED_JSON,
    UNSUPPORTED_VERSION,
    UNKNOWN_FIELD,
    REQUIRED,
    INVALID_FORMAT,
    INVALID_VALUE,
    INVALID_SPAN,
    DUPLICATE_ID,
    UNKNOWN_REFERENCE,
    CYCLE,
    OPERATOR_VALUE_MISMATCH,
    LIMIT_EXCEEDED
  }

  public IrViolation {
    Objects.requireNonNull(path, "path must not be null");
    Objects.requireNonNull(code, "code must not be null");
    Objects.requireNonNull(message, "message must not be null");
  }

  @Override
  public String toString() {
    return path + " " + code + ": " + message;
  }
}

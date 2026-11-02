package io.qwenbridge.core.schema;

import java.util.Objects;

/** Why a QueryIR element does not fit a domain schema (QB-045, QB-048). */
public record SchemaViolation(String path, Code code, String message) {

  public enum Code {
    /** The field is not in the schema, so it can never become an executable filter. */
    UNKNOWN_FIELD,
    OPERATOR_NOT_ALLOWED,
    TYPE_MISMATCH,
    UNIT_MISMATCH,
    OUT_OF_RANGE,
    VALUE_NOT_ALLOWED,
    UNKNOWN_ENTITY_TYPE
  }

  public SchemaViolation {
    Objects.requireNonNull(path, "path must not be null");
    Objects.requireNonNull(code, "code must not be null");
    Objects.requireNonNull(message, "message must not be null");
  }

  @Override
  public String toString() {
    return path + " " + code + ": " + message;
  }
}

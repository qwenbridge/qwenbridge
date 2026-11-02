package io.qwenbridge.core.schema;

import java.util.Objects;

/** Typed registry failure (QB-046, QB-047, QB-048). Messages never contain user data. */
public final class SchemaRegistryException extends RuntimeException {

  public enum Code {
    /** The id and version exist with different content; versions are immutable. */
    IMMUTABLE_VERSION,
    /** A new minor version breaks compatibility; a new major version is required. */
    INCOMPATIBLE_CHANGE,
    /** The caller does not own the schema id it tries to change or remove. */
    OWNERSHIP_VIOLATION,
    /** The requested schema or version does not exist. */
    NOT_FOUND,
    /** The owner identifier is invalid. */
    INVALID_OWNER
  }

  private final Code code;

  public SchemaRegistryException(Code code, String message) {
    super(message, null, false, false);
    this.code = Objects.requireNonNull(code, "code must not be null");
  }

  public Code code() {
    return code;
  }
}

package io.qwenbridge.semantic;

import java.util.Objects;

public record SemanticEntity(String value, SemanticEntityType type, double confidence) {

  public SemanticEntity {
    Objects.requireNonNull(value, "value must not be null");
    Objects.requireNonNull(type, "type must not be null");

    if (value.isBlank()) {
      throw new IllegalArgumentException("value must not be blank");
    }

    if (confidence < 0.0 || confidence > 1.0) {
      throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
    }
  }
}

package io.qwenbridge.intelligence.ir;

import java.util.Objects;

/** Entity with evidence offsets relative to the original query (UTF-16 indices). */
public record QueryEntity(String value, String type, int start, int end) {
  public QueryEntity {
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(type, "type");
    if (value.isBlank() || type.isBlank() || start < 0 || end <= start) {
      throw new IllegalArgumentException("invalid query entity");
    }
  }
}

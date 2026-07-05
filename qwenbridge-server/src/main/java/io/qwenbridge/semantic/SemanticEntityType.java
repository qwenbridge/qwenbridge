package io.qwenbridge.semantic;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Arrays;

public enum SemanticEntityType {
  PRODUCT,
  BRAND,
  CATEGORY,
  ATTRIBUTE,
  PRICE,
  LOCATION,
  LANGUAGE,
  UNKNOWN;

  @JsonCreator
  public static SemanticEntityType from(String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }

    String normalized = value.trim().toUpperCase();

    return Arrays.stream(values())
        .filter(type -> type.name().equals(normalized))
        .findFirst()
        .orElse(UNKNOWN);
  }
}

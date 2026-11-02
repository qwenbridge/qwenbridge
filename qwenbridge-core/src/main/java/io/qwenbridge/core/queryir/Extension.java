package io.qwenbridge.core.queryir;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A domain-specific payload attached under a namespace (QB-150, QB-234), e.g. {@code commerce}.
 * This is the only place where domain detail lives; the Core never interprets it. The owning Domain
 * Pack validates its content against its own schema version.
 *
 * <p>{@code data} holds JSON-like values only: String, BigDecimal, Boolean, List and Map with
 * String keys. It is deeply copied and unmodifiable.
 */
public record Extension(String version, Map<String, Object> data) {

  public Extension {
    Objects.requireNonNull(version, "version must not be null");
    data = data == null ? Map.of() : Map.copyOf(deepCopyMap(data));
  }

  private static Map<String, Object> deepCopyMap(Map<String, Object> source) {
    Map<String, Object> copy = new LinkedHashMap<>();
    source.forEach((k, v) -> copy.put(Objects.requireNonNull(k, "keys must not be null"), copy(v)));
    return copy;
  }

  @SuppressWarnings("unchecked")
  private static Object copy(Object value) {
    if (value instanceof Map<?, ?> map) {
      return Collections.unmodifiableMap(deepCopyMap((Map<String, Object>) map));
    }
    if (value instanceof List<?> list) {
      List<Object> copy = new ArrayList<>();
      list.forEach(v -> copy.add(copy(v)));
      return Collections.unmodifiableList(copy);
    }
    if (value instanceof String || value instanceof BigDecimal || value instanceof Boolean) {
      return value;
    }
    throw new IllegalArgumentException(
        "extension data must be JSON-like (string, number, boolean, list, map)");
  }
}

package io.qwenbridge.core.queryir;

import io.qwenbridge.core.confidence.Confidence;
import java.util.Objects;
import java.util.Optional;

/**
 * A named thing mentioned in the query (QB-029): a brand, a Kubernetes pod, an IP address, a city.
 * {@code type} is a schema-defined entity type, not a fixed Core enum, so new domains need no Core
 * change. {@code value} is the surface form; {@code normalizedValue} an optional canonical form.
 */
public record Entity(
    String id,
    String type,
    String value,
    Optional<String> normalizedValue,
    Span span,
    Confidence confidence) {

  public Entity {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(value, "value must not be null");
    normalizedValue = normalizedValue == null ? Optional.empty() : normalizedValue;
    Objects.requireNonNull(span, "span must not be null");
    Objects.requireNonNull(confidence, "confidence must not be null");
  }
}

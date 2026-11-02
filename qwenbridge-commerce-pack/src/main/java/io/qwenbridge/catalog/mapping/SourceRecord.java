package io.qwenbridge.catalog.mapping;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One record exactly as a catalog source delivered it (QB-093). Field values may be any JSON-like
 * value, including {@code null}; the mapping decides what they mean.
 */
public record SourceRecord(
    String connector, String recordId, Instant observedAt, Map<String, Object> fields) {
  public SourceRecord {
    if (fields == null || fields.size() > 128)
      throw new IllegalArgumentException("Invalid catalog document");
    if (connector == null
        || connector.isBlank()
        || recordId == null
        || recordId.isBlank()
        || recordId.length() > 512
        || observedAt == null) throw new IllegalArgumentException("Invalid catalog provenance");
    fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
  }
}

package io.qwenbridge.catalog.canonical;

import java.time.Instant;

/** The source record a canonical product was built from, and the mapping that read it. */
public record SourceRef(String connector, String recordId, String mapping, Instant observedAt) {
  public SourceRef {
    if (connector == null
        || connector.isBlank()
        || recordId == null
        || recordId.isBlank()
        || mapping == null
        || observedAt == null) throw new IllegalArgumentException("Invalid catalog provenance");
  }
}

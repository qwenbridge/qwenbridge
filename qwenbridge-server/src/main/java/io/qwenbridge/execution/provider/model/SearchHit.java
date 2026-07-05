package io.qwenbridge.execution.provider.model;

import java.util.Map;
import java.util.Objects;

public record SearchHit(
    String id, double score, Map<String, Object> document, Map<String, Object> metadata) {

  public SearchHit {
    Objects.requireNonNull(id, "id must not be null");
    document = Map.copyOf(Objects.requireNonNull(document, "document must not be null"));
    metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata must not be null"));
  }

  public static SearchHit of(String id, double score, Map<String, Object> document) {
    return new SearchHit(id, score, document, Map.of());
  }
}

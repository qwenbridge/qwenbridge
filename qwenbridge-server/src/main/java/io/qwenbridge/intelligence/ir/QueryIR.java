package io.qwenbridge.intelligence.ir;

import java.util.List;
import java.util.Objects;

/** Versioned, immutable intermediate representation. Not an executable search plan. */
public record QueryIR(
    int version,
    String originalQuery,
    List<QueryEntity> entities,
    List<QueryConstraint> constraints) {
  public static final int CURRENT_VERSION = 1;

  public QueryIR {
    if (version != CURRENT_VERSION) {
      throw new IllegalArgumentException("unsupported QueryIR version: " + version);
    }
    Objects.requireNonNull(originalQuery, "originalQuery");
    if (originalQuery.isBlank()) {
      throw new IllegalArgumentException("originalQuery must not be blank");
    }
    entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
    constraints = List.copyOf(Objects.requireNonNull(constraints, "constraints"));
    for (QueryEntity entity : entities) {
      if (entity.end() > originalQuery.length()
          || !originalQuery.substring(entity.start(), entity.end()).equals(entity.value())) {
        throw new IllegalArgumentException("entity evidence does not match original query");
      }
    }
    for (QueryConstraint constraint : constraints) {
      if (constraint.end() > originalQuery.length()) {
        throw new IllegalArgumentException("constraint evidence exceeds original query");
      }
    }
  }
}

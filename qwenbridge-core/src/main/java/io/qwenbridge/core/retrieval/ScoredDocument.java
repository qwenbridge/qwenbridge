package io.qwenbridge.core.retrieval;

import io.qwenbridge.core.confidence.RetrievalScore;
import io.qwenbridge.core.retrieval.filter.FieldValue;
import java.util.Map;
import java.util.Objects;

/**
 * One result (QB-107). The score is a {@link RetrievalScore} with its kind and scorer, so it can
 * never be mistaken for confidence (QB-079).
 *
 * @param rank 1-based position in this provider's result
 */
public record ScoredDocument(
    String id, int rank, RetrievalScore score, Map<String, FieldValue> fields) {

  public ScoredDocument {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(score, "score must not be null");
    fields = fields == null ? Map.of() : Map.copyOf(fields);
    if (rank < 1) {
      throw new IllegalArgumentException("rank is 1-based");
    }
  }
}

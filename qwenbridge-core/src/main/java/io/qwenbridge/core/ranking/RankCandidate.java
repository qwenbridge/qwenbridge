package io.qwenbridge.core.ranking;

import io.qwenbridge.core.confidence.RetrievalScore;
import io.qwenbridge.core.retrieval.filter.FieldValue;
import java.util.Map;
import java.util.Objects;

/**
 * A retrieved result entering ranking.
 *
 * @param retrievalRank the provider's (or fusion's) rank, 1 = best
 * @param retrievalScore kept unchanged through ranking and reranking (QB-120)
 */
public record RankCandidate(
    String id, int retrievalRank, RetrievalScore retrievalScore, Map<String, FieldValue> fields) {
  public RankCandidate {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(retrievalScore, "retrievalScore must not be null");
    if (retrievalRank < 1) {
      throw new IllegalArgumentException("retrievalRank starts at 1");
    }
    fields = fields == null ? Map.of() : Map.copyOf(fields);
  }
}

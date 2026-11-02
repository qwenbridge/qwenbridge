package io.qwenbridge.core.ranking;

import io.qwenbridge.core.confidence.RetrievalScore;
import io.qwenbridge.core.retrieval.filter.FieldValue;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * A ranked result (QB-123).
 *
 * @param position final position, 1 = first
 * @param score the baseline score: the sum of the components' contributions. It is an ordinal
 *     ranking value, comparable only within one ranking of one query; it is not a probability,
 *     confidence or relevance grade.
 * @param retrievalScore the provider's original score, unchanged
 * @param rerankPosition the reranker's position, when a reranker ordered this item
 */
public record RankedItem(
    String id,
    int position,
    double score,
    int retrievalRank,
    RetrievalScore retrievalScore,
    List<ScoreComponent> components,
    Map<String, FieldValue> fields,
    OptionalInt rerankPosition) {
  public RankedItem {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(retrievalScore, "retrievalScore must not be null");
    components = List.copyOf(components);
    fields = fields == null ? Map.of() : Map.copyOf(fields);
    rerankPosition = rerankPosition == null ? OptionalInt.empty() : rerankPosition;
    if (!Double.isFinite(score)) {
      throw new IllegalArgumentException("score must be finite");
    }
  }

  RankedItem at(int newPosition) {
    return new RankedItem(
        id, newPosition, score, retrievalRank, retrievalScore, components, fields, rerankPosition);
  }
}

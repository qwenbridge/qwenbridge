package io.qwenbridge.core.retrieval.hybrid;

import io.qwenbridge.core.confidence.RetrievalScore;
import io.qwenbridge.core.retrieval.filter.FieldValue;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A document after fusion (QB-113). The fused score is a HYBRID_FUSION retrieval score; every
 * source's own rank and original score are kept unchanged as provenance.
 */
public record FusedDocument(
    String id,
    int rank,
    RetrievalScore fusedScore,
    List<SourceRank> sources,
    Map<String, FieldValue> fields) {

  public FusedDocument {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(fusedScore, "fusedScore must not be null");
    sources = List.copyOf(sources);
    fields = fields == null ? Map.of() : Map.copyOf(fields);
    if (sources.isEmpty()) {
      throw new IllegalArgumentException("a fused document comes from at least one source");
    }
  }

  public int bestSourceRank() {
    return sources.stream().mapToInt(SourceRank::rank).min().orElseThrow();
  }
}

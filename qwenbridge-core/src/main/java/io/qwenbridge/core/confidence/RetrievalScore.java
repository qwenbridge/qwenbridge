package io.qwenbridge.core.confidence;

import java.util.Objects;

/**
 * A retrieval or ranking score (QB-079): how well one document matched one query in one backend. It
 * is a separate type from {@link Confidence} on purpose and cannot be converted into one: BM25,
 * vector similarity and rank positions are not probabilities, are not comparable across queries or
 * backends, and say nothing about whether the query was understood correctly.
 */
public record RetrievalScore(double value, Kind kind, String scorer) {

  public enum Kind {
    BM25,
    VECTOR_SIMILARITY,
    HYBRID_FUSION,
    RERANK_POSITION,
    BACKEND_NATIVE
  }

  public RetrievalScore {
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(scorer, "scorer must not be null");
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("score must be finite");
    }
  }

  /** Scores are only comparable within one query and one scorer. */
  public boolean comparableWith(RetrievalScore other) {
    return other != null && kind == other.kind && scorer.equals(other.scorer);
  }
}

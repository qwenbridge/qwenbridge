package io.qwenbridge.core.retrieval.hybrid;

import io.qwenbridge.core.confidence.RetrievalScore;
import java.util.Objects;

/** Where one source ranked a document, and with what score (QB-112, QB-113). */
public record SourceRank(String source, int rank, RetrievalScore score) {

  public SourceRank {
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(score, "score must not be null");
    if (rank < 1) {
      throw new IllegalArgumentException("rank is 1-based");
    }
  }
}

package io.qwenbridge.core.retrieval.hybrid;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Result of hybrid retrieval (QB-112, QB-115). {@code executed} is what actually ran, never what
 * was asked for: when the vector side could not run, the result says KEYWORD_ONLY and lists why in
 * {@code degradations}, so lexical results are never presented as semantic.
 */
public record HybridResult(
    Strategy requested,
    Strategy executed,
    List<FusedDocument> documents,
    List<Degradation> degradations,
    Duration took) {

  public enum Strategy {
    HYBRID,
    KEYWORD_ONLY,
    VECTOR_ONLY,
    NONE
  }

  /** Why part of the requested retrieval did not run. */
  public record Degradation(String component, Reason reason, String detail) {
    public enum Reason {
      EMBEDDING_UNAVAILABLE,
      REINDEX_REQUIRED,
      PROVIDER_UNSUPPORTED,
      PROVIDER_FAILED
    }

    public Degradation {
      Objects.requireNonNull(component, "component must not be null");
      Objects.requireNonNull(reason, "reason must not be null");
      Objects.requireNonNull(detail, "detail must not be null");
    }
  }

  public HybridResult {
    Objects.requireNonNull(requested, "requested must not be null");
    Objects.requireNonNull(executed, "executed must not be null");
    documents = List.copyOf(documents);
    degradations = List.copyOf(degradations);
    Objects.requireNonNull(took, "took must not be null");
  }

  public boolean degraded() {
    return !degradations.isEmpty();
  }
}

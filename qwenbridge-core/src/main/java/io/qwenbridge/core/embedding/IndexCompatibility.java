package io.qwenbridge.core.embedding;

import java.util.ArrayList;
import java.util.List;

/** Whether vectors of one space can be used with an index built in another (QB-111). */
public record IndexCompatibility(boolean compatible, List<Reason> reindexReasons) {

  public enum Reason {
    MODEL_CHANGED,
    VERSION_CHANGED,
    DIMENSIONS_CHANGED
  }

  public IndexCompatibility {
    reindexReasons = List.copyOf(reindexReasons);
  }

  public static IndexCompatibility check(EmbeddingSpace index, EmbeddingSpace current) {
    List<Reason> reasons = new ArrayList<>();
    if (!index.model().equals(current.model())) {
      reasons.add(Reason.MODEL_CHANGED);
    }
    if (!index.version().equals(current.version())) {
      reasons.add(Reason.VERSION_CHANGED);
    }
    if (index.dimensions() != current.dimensions()) {
      reasons.add(Reason.DIMENSIONS_CHANGED);
    }
    return new IndexCompatibility(reasons.isEmpty(), reasons);
  }

  public boolean reindexRequired() {
    return !compatible;
  }
}

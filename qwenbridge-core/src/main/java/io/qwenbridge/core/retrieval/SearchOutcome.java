package io.qwenbridge.core.retrieval;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Typed result of a provider call (QB-107, QB-104): success, unsupported or failed. */
public sealed interface SearchOutcome {

  /**
   * @param matched documents that matched before the topK cut
   */
  record Success(List<ScoredDocument> hits, long matched, Duration took) implements SearchOutcome {
    public Success {
      hits = List.copyOf(hits);
      Objects.requireNonNull(took, "took must not be null");
    }
  }

  /** The provider cannot serve this request as asked; nothing was executed. */
  record Unsupported(String requirement) implements SearchOutcome {
    public Unsupported {
      Objects.requireNonNull(requirement, "requirement must not be null");
    }
  }

  record Failed(Failure failure, String detail) implements SearchOutcome {
    public Failed {
      Objects.requireNonNull(failure, "failure must not be null");
      Objects.requireNonNull(detail, "detail must not be null");
    }
  }

  enum Failure {
    TIMEOUT,
    CANCELLED,
    UNAVAILABLE,
    INVALID_REQUEST
  }
}

package io.qwenbridge.core.embedding;

import java.util.List;
import java.util.Objects;

/** Typed result of an embedding call (QB-110). Failures are values, never exceptions. */
public sealed interface EmbeddingResult {

  /** One vector per input text, in input order, all in {@code space}. */
  record Embedded(List<double[]> vectors, EmbeddingSpace space) implements EmbeddingResult {
    public Embedded {
      vectors = vectors.stream().map(double[]::clone).toList();
      Objects.requireNonNull(space, "space must not be null");
    }

    @Override
    public List<double[]> vectors() {
      return vectors.stream().map(double[]::clone).toList();
    }
  }

  record Failed(Failure failure, String providerId) implements EmbeddingResult {
    public Failed {
      Objects.requireNonNull(failure, "failure must not be null");
      Objects.requireNonNull(providerId, "providerId must not be null");
    }
  }

  enum Failure {
    /** No provider is configured. */
    NO_PROVIDER,
    UNAVAILABLE,
    TIMEOUT,
    INPUT_TOO_LARGE,
    BATCH_TOO_LARGE,
    /** Wrong count, wrong dimensions or non-finite values. */
    INVALID_OUTPUT
  }
}

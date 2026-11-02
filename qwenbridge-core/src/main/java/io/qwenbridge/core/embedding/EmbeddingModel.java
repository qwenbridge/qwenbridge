package io.qwenbridge.core.embedding;

import java.util.Objects;

/**
 * Metadata of an embedding model (QB-110).
 *
 * @param maxBatch largest number of texts per call
 * @param maxInputChars largest single input
 */
public record EmbeddingModel(EmbeddingSpace space, int maxBatch, int maxInputChars) {

  public EmbeddingModel {
    Objects.requireNonNull(space, "space must not be null");
    if (maxBatch < 1 || maxInputChars < 1) {
      throw new IllegalArgumentException("limits must be positive");
    }
  }
}

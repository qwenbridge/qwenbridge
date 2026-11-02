package io.qwenbridge.core.embedding;

import java.util.Objects;

/**
 * The vector space an embedding belongs to (QB-111): model, model version and dimensions. Vectors
 * are only comparable within one space; any change requires reindexing.
 */
public record EmbeddingSpace(String model, String version, int dimensions) {

  public EmbeddingSpace {
    Objects.requireNonNull(model, "model must not be null");
    Objects.requireNonNull(version, "version must not be null");
    if (model.isBlank() || version.isBlank()) {
      throw new IllegalArgumentException("model and version must be named");
    }
    if (dimensions < 1 || dimensions > 65_536) {
      throw new IllegalArgumentException("dimensions out of range");
    }
  }

  @Override
  public String toString() {
    return model + "@" + version + "/" + dimensions;
  }
}

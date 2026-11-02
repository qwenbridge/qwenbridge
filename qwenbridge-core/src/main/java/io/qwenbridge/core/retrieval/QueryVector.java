package io.qwenbridge.core.retrieval;

import io.qwenbridge.core.embedding.EmbeddingSpace;
import java.util.Arrays;
import java.util.Objects;

/**
 * A query embedding and the space it was produced in (QB-109, QB-111). Carrying the space makes it
 * impossible to compare vectors from different models or dimensions by accident.
 */
public final class QueryVector {

  private final double[] values;
  private final EmbeddingSpace space;

  public QueryVector(double[] values, EmbeddingSpace space) {
    this.space = Objects.requireNonNull(space, "space must not be null");
    this.values = Objects.requireNonNull(values, "values must not be null").clone();
    if (this.values.length != space.dimensions()) {
      throw new IllegalArgumentException("vector length does not match the embedding space");
    }
    for (double v : this.values) {
      if (!Double.isFinite(v)) {
        throw new IllegalArgumentException("vector values must be finite");
      }
    }
  }

  public double[] values() {
    return values.clone();
  }

  public EmbeddingSpace space() {
    return space;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof QueryVector q && q.space.equals(space) && Arrays.equals(q.values, values);
  }

  @Override
  public int hashCode() {
    return 31 * space.hashCode() + Arrays.hashCode(values);
  }
}

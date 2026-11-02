package io.qwenbridge.ai.contract;

import java.util.List;

public record EmbeddingResponse(List<Double> vector) {
  public EmbeddingResponse {
    vector = vector == null ? List.of() : List.copyOf(vector);
  }
}

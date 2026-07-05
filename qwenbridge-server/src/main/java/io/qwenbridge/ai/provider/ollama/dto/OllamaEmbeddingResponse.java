package io.qwenbridge.ai.provider.ollama.dto;

import java.util.List;

public record OllamaEmbeddingResponse(List<List<Double>> embeddings) {

  public List<Double> firstEmbedding() {
    if (embeddings == null || embeddings.isEmpty()) {
      return List.of();
    }

    List<Double> first = embeddings.getFirst();

    if (first == null) {
      return List.of();
    }

    return first;
  }
}

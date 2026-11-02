package io.qwenbridge.ai.provider.ollama.dto;

import java.util.List;

public record OllamaEmbeddingResponse(List<List<Double>> embeddings) {
  public OllamaEmbeddingResponse {
    embeddings =
        embeddings == null
            ? List.of()
            : List.copyOf(
                embeddings.stream()
                    .map(v -> v == null ? List.<Double>of() : List.copyOf(v))
                    .toList());
  }

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

package io.omnisearch.ai.provider.ollama.dto;

public record OllamaEmbeddingRequest(
        String model,
        String input
) {
}

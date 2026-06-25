package io.omnisearch.ai.provider.ollama.dto;

import java.util.List;

public record OllamaEmbeddingResponse(
        List<Double> embedding
) {
}

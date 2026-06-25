package io.qwenbridge.ai.provider.ollama.dto;

import java.util.List;

public record OllamaEmbeddingResponse(
        List<Double> embedding
) {
}

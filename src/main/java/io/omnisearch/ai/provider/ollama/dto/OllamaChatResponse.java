package io.omnisearch.ai.provider.ollama.dto;

public record OllamaChatResponse(
        String model,
        Message message,
        boolean done
) {

    public record Message(
            String role,
            String content
    ) {
    }
}

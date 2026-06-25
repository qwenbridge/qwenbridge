package io.qwenbridge.ai.provider.ollama.client;

import io.qwenbridge.ai.exception.AIException;
import io.qwenbridge.ai.provider.ollama.dto.OllamaChatRequest;
import io.qwenbridge.ai.provider.ollama.dto.OllamaChatResponse;
import io.qwenbridge.ai.provider.ollama.dto.OllamaEmbeddingRequest;
import io.qwenbridge.ai.provider.ollama.dto.OllamaEmbeddingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final WebClient webClient;

    public OllamaClient(@Qualifier("ollamaWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public OllamaChatResponse chat(OllamaChatRequest request) {
        log.debug("Sending Ollama chat request. model={}", request.model());

        return webClient.post()
                .uri("/api/chat")
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        OllamaExceptionHandler::mapError)
                .bodyToMono(OllamaChatResponse.class)
                .blockOptional()
                .orElseThrow(() -> new AIException("Ollama chat response was empty"));
    }

    public OllamaEmbeddingResponse embed(OllamaEmbeddingRequest request) {
        log.debug("Sending Ollama embedding request. model={}", request.model());

        return webClient.post()
                .uri("/api/embed")
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        OllamaExceptionHandler::mapError)
                .bodyToMono(OllamaEmbeddingResponse.class)
                .blockOptional()
                .orElseThrow(() -> new AIException("Ollama embedding response was empty"));
    }
}

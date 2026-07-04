package io.qwenbridge.ai.provider.ollama.client;

import lombok.extern.slf4j.Slf4j;
import io.qwenbridge.ai.exception.AIException;
import io.qwenbridge.ai.provider.ollama.config.OllamaProperties;
import io.qwenbridge.ai.provider.ollama.dto.OllamaChatRequest;
import io.qwenbridge.ai.provider.ollama.dto.OllamaChatResponse;
import io.qwenbridge.ai.provider.ollama.dto.OllamaEmbeddingRequest;
import io.qwenbridge.ai.provider.ollama.dto.OllamaEmbeddingResponse;
import io.qwenbridge.operations.metrics.OperationsMetrics;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import io.qwenbridge.ai.provider.ollama.dto.OllamaStreamingChatResponse;
import reactor.core.publisher.Flux;

import java.time.Duration;


@Component
@Slf4j
public class OllamaClient {

    private final WebClient webClient;
    private final OllamaProperties properties;
    private final OperationsMetrics metrics;

    public OllamaClient(
            @Qualifier("ollamaWebClient") WebClient webClient,
            OllamaProperties properties,
            OperationsMetrics metrics
    ) {
        this.webClient = webClient;
        this.properties = properties;
        this.metrics = metrics;
    }

    public OllamaChatResponse chat(OllamaChatRequest request) {
        log.debug("Sending Ollama chat request. model={}", request.model());

        return execute(
                "chat",
                webClient.post()
                        .uri("/api/chat")
                        .bodyValue(request)
                        .retrieve()
                        .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                                OllamaExceptionHandler::mapError)
                        .bodyToMono(OllamaChatResponse.class),
                "Ollama chat response was empty"
        );
    }

    public Flux<OllamaStreamingChatResponse> streamChat(OllamaChatRequest request) {
        log.debug("Sending Ollama streaming chat request. model={}", request.model());

        long started = System.nanoTime();

        Flux<OllamaStreamingChatResponse> pipeline = webClient.post()
                .uri("/api/chat")
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        OllamaExceptionHandler::mapError)
                .bodyToFlux(OllamaStreamingChatResponse.class)
                .timeout(properties.readTimeout())
                .doOnComplete(() -> recordProvider("stream", "success", started))
                .doOnError(throwable -> recordProvider("stream", "failure", started))
                .onErrorMap(
                        throwable -> throwable instanceof AIException
                                ? throwable
                                : new AIException("Ollama streaming chat request failed", throwable)
                );

        return pipeline;
    }

    public OllamaEmbeddingResponse embed(OllamaEmbeddingRequest request) {
        log.debug("Sending Ollama embedding request. model={}", request.model());

        return execute(
                "embedding",
                webClient.post()
                        .uri("/api/embed")
                        .bodyValue(request)
                        .retrieve()
                        .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                                OllamaExceptionHandler::mapError)
                        .bodyToMono(OllamaEmbeddingResponse.class),
                "Ollama embedding response was empty"
        );
    }

    private <T> T execute(
            String operation,
            Mono<T> response,
            String emptyResponseMessage
    ) {
        try {
            Mono<T> pipeline = response;

            if (properties.retryCount() > 0) {
                pipeline = pipeline.retryWhen(retrySpec(operation));
            }

            long started = System.nanoTime();
            T result = pipeline
                    .blockOptional(properties.readTimeout())
                    .orElseThrow(() -> new AIException(emptyResponseMessage));
            recordProvider(operation, "success", started);
            return result;
        } catch (AIException exception) {
            recordProvider(operation, "failure", System.nanoTime());
            throw exception;
        } catch (RuntimeException exception) {
            recordProvider(operation, "failure", System.nanoTime());
            throw new AIException(
                    "Ollama %s request failed".formatted(operation),
                    exception
            );
        }
    }

    private Retry retrySpec(String operation) {
        return Retry.max(properties.retryCount())
                .filter(this::isRetryable)
                .doBeforeRetry(signal -> log.warn(
                        "Retrying Ollama {} request. attempt={} maxAttempts={} reason={}",
                        operation,
                        signal.totalRetries() + 1,
                        properties.retryCount(),
                        signal.failure().getMessage()
                ))
                .onRetryExhaustedThrow((spec, signal) -> new AIException(
                        "Ollama %s request failed after %d retry attempt(s)"
                                .formatted(operation, properties.retryCount()),
                        signal.failure()
                ));
    }

    private boolean isRetryable(Throwable throwable) {
        return !(throwable instanceof IllegalArgumentException);
    }

    private void recordProvider(String operation, String outcome, long started) {
        metrics.recordAiProvider("ollama", operation, outcome, Duration.ofNanos(Math.max(0, System.nanoTime() - started)));
    }

}

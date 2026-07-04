package io.qwenbridge.ai.provider.ollama.client;

import io.qwenbridge.operations.metrics.OperationsMetrics;
import io.qwenbridge.ai.exception.AIException;
import io.qwenbridge.ai.provider.ollama.config.OllamaProperties;
import io.qwenbridge.ai.provider.ollama.dto.OllamaChatRequest;
import io.qwenbridge.ai.provider.ollama.dto.OllamaChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OllamaClientReliabilityTest {

    @Test
    void shouldRetryFailedChatRequestWithinConfiguredBound() {
        AtomicInteger attempts = new AtomicInteger();

        OllamaClient client = client(
                webClient(request -> {
                    if (attempts.incrementAndGet() == 1) {
                        return Mono.just(ClientResponse
                                .create(HttpStatus.SERVICE_UNAVAILABLE)
                                .body("temporary failure")
                                .build());
                    }

                    return Mono.just(ClientResponse
                            .create(HttpStatus.OK)
                            .header("Content-Type", "application/json")
                            .body("""
                                    {
                                      "model": "qwen2.5",
                                      "message": {
                                        "role": "assistant",
                                        "content": "ok"
                                      },
                                      "done": true
                                    }
                                    """)
                            .build());
                }),
                properties(1, Duration.ofSeconds(2))
        );

        OllamaChatResponse response = client.chat(chatRequest());

        assertThat(response.message().content()).isEqualTo("ok");
        assertThat(attempts).hasValue(2);
    }

    @Test
    void shouldStopRetryingAfterConfiguredRetryCount() {
        AtomicInteger attempts = new AtomicInteger();

        OllamaClient client = client(
                webClient(request -> {
                    attempts.incrementAndGet();
                    return Mono.just(ClientResponse
                            .create(HttpStatus.BAD_GATEWAY)
                            .body("provider unavailable")
                            .build());
                }),
                properties(2, Duration.ofSeconds(2))
        );

        assertThatThrownBy(() -> client.chat(chatRequest()))
                .isInstanceOf(AIException.class)
                .hasMessageContaining("failed after 2 retry attempt");

        assertThat(attempts).hasValue(3);
    }

    @Test
    void shouldFailDeterministicallyWhenReadTimeoutIsExceeded() {
        OllamaClient client = client(
                webClient(request -> Mono.never()),
                properties(0, Duration.ofMillis(50))
        );

        assertThatThrownBy(() -> client.chat(chatRequest()))
                .isInstanceOf(AIException.class)
                .hasMessageContaining("Ollama chat request failed");
    }

    @Test
    void shouldDisableRetryWhenRetryCountIsZero() {
        AtomicInteger attempts = new AtomicInteger();

        OllamaClient client = client(
                webClient(request -> {
                    attempts.incrementAndGet();
                    return Mono.just(ClientResponse
                            .create(HttpStatus.SERVICE_UNAVAILABLE)
                            .body("temporary failure")
                            .build());
                }),
                properties(0, Duration.ofSeconds(2))
        );

        assertThatThrownBy(() -> client.chat(chatRequest()))
                .isInstanceOf(AIException.class)
                .hasMessageContaining("Ollama request failed");

        assertThat(attempts).hasValue(1);
    }

    private WebClient webClient(ExchangeFunction exchangeFunction) {
        return WebClient.builder()
                .baseUrl("http://localhost:11434")
                .exchangeFunction(exchangeFunction)
                .build();
    }

    private OllamaProperties properties(
            int retryCount,
            Duration readTimeout
    ) {
        return new OllamaProperties(
                URI.create("http://localhost:11434"),
                "qwen2.5",
                "bge-m3",
                Duration.ofSeconds(1),
                readTimeout,
                retryCount,
                false
        );
    }

    private OllamaChatRequest chatRequest() {
        return new OllamaChatRequest(
                "qwen2.5",
                List.of(new OllamaChatRequest.Message("user", "hello")),
                false
        );
    }

    private OllamaClient client(WebClient webClient, OllamaProperties properties) {
        return new OllamaClient(
                webClient,
                properties,
                mock(OperationsMetrics.class)
        );
    }


}

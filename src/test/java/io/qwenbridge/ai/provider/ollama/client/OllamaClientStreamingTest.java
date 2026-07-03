package io.qwenbridge.ai.provider.ollama.client;

import io.qwenbridge.ai.exception.AIException;
import io.qwenbridge.ai.provider.ollama.config.OllamaProperties;
import io.qwenbridge.ai.provider.ollama.dto.OllamaChatRequest;
import io.qwenbridge.ai.provider.ollama.dto.OllamaStreamingChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaClientStreamingTest {

    @Test
    void shouldReadStreamingChatChunksInOrder() {
        OllamaClient client = new OllamaClient(
                webClient(request -> reactor.core.publisher.Mono.just(ClientResponse
                        .create(HttpStatus.OK)
                        .header("Content-Type", "application/x-ndjson")
                        .body(Flux.concat(
                                json("""
                        {"model":"qwen2.5","message":{"role":"assistant","content":"hel"},"done":false}
                        """),
                                json("""
                        {"model":"qwen2.5","message":{"role":"assistant","content":"lo"},"done":false}
                        """),
                                json("""
                        {"model":"qwen2.5","message":{"role":"assistant","content":""},"done":true}
                        """)
                        ))
                        .build())),
                properties(0, Duration.ofSeconds(2))
        );

        List<OllamaStreamingChatResponse> responses = client.streamChat(streamingChatRequest())
                .collectList()
                .block();

        assertThat(responses).hasSize(3);
        assertThat(responses.get(0).content()).isEqualTo("hel");
        assertThat(responses.get(1).content()).isEqualTo("lo");
        assertThat(responses.get(2).done()).isTrue();
    }

    @Test
    void shouldFailStreamingChatWhenProviderReturnsError() {
        OllamaClient client = new OllamaClient(
                webClient(request -> MonoResponse.error(HttpStatus.BAD_GATEWAY, "provider failed")),
                properties(0, Duration.ofSeconds(2))
        );

        assertThatThrownBy(() -> client.streamChat(streamingChatRequest()).collectList().block())
                .isInstanceOf(AIException.class)
                .hasMessageContaining("Ollama request failed");
    }

    @Test
    void shouldNotRetryStreamingChatAfterFailure() {
        AtomicInteger attempts = new AtomicInteger();

        OllamaClient client = new OllamaClient(
                webClient(request -> {
                    attempts.incrementAndGet();
                    return MonoResponse.error(HttpStatus.SERVICE_UNAVAILABLE, "temporary failure");
                }),
                properties(3, Duration.ofSeconds(2))
        );

        assertThatThrownBy(() -> client.streamChat(streamingChatRequest()).collectList().block())
                .isInstanceOf(AIException.class);

        assertThat(attempts).hasValue(1);
    }

    private WebClient webClient(ExchangeFunction exchangeFunction) {
        return WebClient.builder()
                .baseUrl("http://localhost:11434")
                .exchangeFunction(exchangeFunction)
                .build();
    }

    private Flux<org.springframework.core.io.buffer.DataBuffer> json(String value) {
        return Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(
                value.getBytes(StandardCharsets.UTF_8)
        ));
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

    private OllamaChatRequest streamingChatRequest() {
        return new OllamaChatRequest(
                "qwen2.5",
                List.of(new OllamaChatRequest.Message("user", "hello")),
                true
        );
    }

    private static final class MonoResponse {
        static reactor.core.publisher.Mono<ClientResponse> error(HttpStatus status, String body) {
            return reactor.core.publisher.Mono.just(ClientResponse
                    .create(status)
                    .body(body)
                    .build());
        }
    }
}

package io.qwenbridge.ai.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.ai.config.AIProperties;
import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.contract.EmbeddingRequest;
import io.qwenbridge.ai.contract.EmbeddingResponse;
import io.qwenbridge.ai.contract.StreamingChatChunk;
import io.qwenbridge.ai.contract.StreamingChatRequest;
import io.qwenbridge.ai.exception.AIException;
import io.qwenbridge.ai.provider.http.JsonHttpProvider;
import io.qwenbridge.ai.provider.ollama.config.OllamaProperties;
import io.qwenbridge.ai.provider.resolver.DefaultAIProviderResolver;
import io.qwenbridge.ai.provider.spi.AIProvider;
import io.qwenbridge.ai.provider.spi.AIProviderRegistry;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.ai.value.ProviderId;
import io.smallrye.mutiny.Multi;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * QB-243, QB-248 in the server: the configured primary answers unless fallbacks are configured; a
 * fallback answer is labelled; without fallbacks the primary's own error is reported unchanged.
 */
class ChatRoutingTest {

  static final class Fake implements AIProvider {
    final String id;
    final AtomicInteger calls = new AtomicInteger();
    volatile RuntimeException failure;

    Fake(String id) {
      this.id = id;
    }

    public ProviderId providerId() {
      return new ProviderId(id);
    }

    public ChatResponse chat(ChatRequest request) {
      calls.incrementAndGet();
      if (failure != null) throw failure;
      return new ChatResponse(id + ": " + request.prompt());
    }

    public Multi<StreamingChatChunk> streamChat(StreamingChatRequest request) {
      return Multi.createFrom().items(new StreamingChatChunk(id, true));
    }

    public EmbeddingResponse embed(EmbeddingRequest request) {
      return new EmbeddingResponse(List.of(1.0));
    }
  }

  final Fake ollama = new Fake("ollama");
  final Fake backup = new Fake("backup");
  final JsonHttpProvider gateway = new JsonHttpProvider(new ObjectMapper(), "", "");
  final AIProviderRegistry registry =
      id ->
          switch (id.value()) {
            case "ollama" -> ollama;
            case "backup" -> backup;
            case "http-json" -> gateway;
            default -> throw new IllegalArgumentException("Unknown AI provider: " + id);
          };
  final OllamaProperties properties =
      new OllamaProperties(null, "qwen2.5", null, null, null, 0, true);

  ChatRouting routing(String primary, List<String> fallbacks) {
    return new ChatRouting(
        registry,
        properties,
        gateway,
        primary,
        Optional.of(fallbacks),
        "3",
        32_000,
        Duration.ofSeconds(5));
  }

  AIService service(String primary, List<String> fallbacks) {
    return new AIService(
        new DefaultAIProviderResolver(registry, new AIProperties(primary)),
        routing(primary, fallbacks));
  }

  @Test
  void thePrimaryIsSelectedByConfigurationAndReportedAsSuch() {
    var served = service("ollama", List.of()).chatServed(new ChatRequest("hi"));
    assertThat(served.response().content()).isEqualTo("ollama: hi");
    assertThat(served.provider()).isEqualTo("ollama");
    assertThat(served.fallback()).isFalse();
    assertThat(service("backup", List.of()).chatServed(new ChatRequest("hi")).provider())
        .isEqualTo("backup");
    var model = routing("ollama", List.of()).router().providers().getFirst().model();
    assertThat(model.toString()).isEqualTo("ollama:qwen2.5@3");
  }

  @Test
  void withoutFallbacksThePrimaryErrorIsUnchanged() {
    ollama.failure = new jakarta.ws.rs.ServiceUnavailableException("ollama down");
    assertThatThrownBy(() -> service("ollama", List.of()).chat(new ChatRequest("hi")))
        .isInstanceOf(jakarta.ws.rs.ServiceUnavailableException.class);
    assertThat(backup.calls.get()).isZero();
  }

  @Test
  void configuredFallbacksAnswerAfterAProviderFaultAndSaySo() {
    ollama.failure = new IllegalStateException("connection refused");
    var served = service("ollama", List.of("backup")).chatServed(new ChatRequest("hi"));
    assertThat(served.response().content()).isEqualTo("backup: hi");
    assertThat(served.provider()).isEqualTo("backup");
    assertThat(served.fallback()).isTrue();
    backup.failure = new IllegalStateException("also down");
    assertThatThrownBy(() -> service("ollama", List.of("backup")).chat(new ChatRequest("hi")))
        .isInstanceOf(AIException.class)
        .hasMessageContaining("ollama")
        .hasMessageContaining("backup");
  }

  @Test
  void misconfiguredFallbacksAreRefusedAtStartup() {
    assertThatThrownBy(() -> routing("ollama", List.of("nonexistent")))
        .hasMessageContaining("Unknown AI provider in configuration: nonexistent");
    assertThatThrownBy(() -> routing("ollama", List.of("http-json")))
        .hasMessageContaining("http-json is not configured");
    assertThatThrownBy(() -> routing("ollama", List.of("ollama")))
        .hasMessageContaining("duplicate chat provider");
    assertThat(Map.of("fallbacks", routing("ollama", List.of("backup")).fallbacks()))
        .containsEntry("fallbacks", List.of("backup"));
  }
}

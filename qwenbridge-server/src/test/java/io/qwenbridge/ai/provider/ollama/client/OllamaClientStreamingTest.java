package io.qwenbridge.ai.provider.ollama.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.qwenbridge.ai.exception.AIException;
import io.qwenbridge.ai.provider.ollama.config.OllamaProperties;
import io.qwenbridge.ai.provider.ollama.dto.OllamaChatRequest;
import io.qwenbridge.ai.provider.ollama.dto.OllamaChatResponse;
import io.qwenbridge.ai.provider.ollama.dto.OllamaStreamingChatResponse;
import io.qwenbridge.operations.metrics.OperationsMetrics;
import io.smallrye.mutiny.Multi;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class OllamaClientStreamingTest {

  private final OllamaApiClient api = mock(OllamaApiClient.class);

  @Test
  void shouldReadStreamingChatChunksInOrder() {
    when(api.streamChat(any()))
        .thenReturn(
            Multi.createFrom()
                .items(chunk("hel", false), chunk("lo", false), chunk("", true)));

    OllamaClient client = client(properties(0));

    List<OllamaStreamingChatResponse> responses =
        client.streamChat(streamingChatRequest()).collectList().block();

    assertThat(responses).hasSize(3);
    assertThat(responses.get(0).content()).isEqualTo("hel");
    assertThat(responses.get(1).content()).isEqualTo("lo");
    assertThat(responses.get(2).done()).isTrue();
  }

  @Test
  void shouldFailStreamingChatWhenProviderReturnsError() {
    WebApplicationException error = httpError(502, "provider failed");
    when(api.streamChat(any())).thenReturn(Multi.createFrom().failure(error));

    OllamaClient client = client(properties(0));

    assertThatThrownBy(() -> client.streamChat(streamingChatRequest()).collectList().block())
        .isInstanceOf(AIException.class)
        .hasMessageContaining("Ollama request failed");
  }

  @Test
  void shouldNotRetryStreamingChatAfterFailure() {
    WebApplicationException error = httpError(503, "temporary failure");
    when(api.streamChat(any())).thenReturn(Multi.createFrom().failure(error));

    OllamaClient client = client(properties(3));

    assertThatThrownBy(() -> client.streamChat(streamingChatRequest()).collectList().block())
        .isInstanceOf(AIException.class);

    verify(api, times(1)).streamChat(any());
  }

  private OllamaStreamingChatResponse chunk(String content, boolean done) {
    return new OllamaStreamingChatResponse(
        "qwen2.5", new OllamaChatResponse.Message("assistant", content), done);
  }

  private WebApplicationException httpError(int status, String body) {
    Response response = mock(Response.class);
    when(response.getStatus()).thenReturn(status);
    when(response.hasEntity()).thenReturn(true);
    when(response.readEntity(String.class)).thenReturn(body);
    return new WebApplicationException("http " + status, response);
  }

  private OllamaProperties properties(int retryCount) {
    return new OllamaProperties(
        URI.create("http://localhost:0"),
        "qwen2.5",
        "bge-m3",
        Duration.ofSeconds(1),
        Duration.ofSeconds(2),
        retryCount,
        true);
  }

  private OllamaChatRequest streamingChatRequest() {
    return new OllamaChatRequest(
        "qwen2.5", List.of(new OllamaChatRequest.Message("user", "hello")), true);
  }

  private OllamaClient client(OllamaProperties properties) {
    return new OllamaClient(api, properties, mock(OperationsMetrics.class));
  }
}

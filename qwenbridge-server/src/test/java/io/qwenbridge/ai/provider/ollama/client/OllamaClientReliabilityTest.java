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
import io.qwenbridge.operations.metrics.OperationsMetrics;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class OllamaClientReliabilityTest {

  private final OllamaApiClient api = mock(OllamaApiClient.class);

  @Test
  void shouldRetryFailedChatRequestWithinConfiguredBound() {
    WebApplicationException error = httpError(503, "temporary failure");
    when(api.chat(any()))
        .thenThrow(error)
        .thenReturn(
            new OllamaChatResponse(
                "qwen2.5", new OllamaChatResponse.Message("assistant", "ok"), true));

    OllamaClient client = client(properties(1));

    OllamaChatResponse response = client.chat(chatRequest());

    assertThat(response.message().content()).isEqualTo("ok");
    verify(api, times(2)).chat(any());
  }

  @Test
  void shouldStopRetryingAfterConfiguredRetryCount() {
    WebApplicationException error = httpError(502, "provider unavailable");
    when(api.chat(any())).thenThrow(error);

    OllamaClient client = client(properties(2));

    assertThatThrownBy(() -> client.chat(chatRequest()))
        .isInstanceOf(AIException.class)
        .hasMessageContaining("Ollama request failed");

    verify(api, times(3)).chat(any());
  }

  @Test
  void shouldFailDeterministicallyWhenReadTimeoutIsExceeded() {
    when(api.chat(any())).thenThrow(new ProcessingException("read timed out"));

    OllamaClient client = client(properties(0));

    assertThatThrownBy(() -> client.chat(chatRequest()))
        .isInstanceOf(AIException.class)
        .hasMessageContaining("Ollama request failed");
  }

  @Test
  void shouldDisableRetryWhenRetryCountIsZero() {
    WebApplicationException error = httpError(503, "temporary failure");
    when(api.chat(any())).thenThrow(error);

    OllamaClient client = client(properties(0));

    assertThatThrownBy(() -> client.chat(chatRequest()))
        .isInstanceOf(AIException.class)
        .hasMessageContaining("Ollama request failed");

    verify(api, times(1)).chat(any());
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
        false);
  }

  private OllamaChatRequest chatRequest() {
    return new OllamaChatRequest(
        "qwen2.5", List.of(new OllamaChatRequest.Message("user", "hello")), false);
  }

  private OllamaClient client(OllamaProperties properties) {
    return new OllamaClient(api, properties, mock(OperationsMetrics.class));
  }
}

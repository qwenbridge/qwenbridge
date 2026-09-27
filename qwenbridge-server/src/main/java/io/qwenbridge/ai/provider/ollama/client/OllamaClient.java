package io.qwenbridge.ai.provider.ollama.client;

import io.qwenbridge.ai.exception.AIException;
import io.qwenbridge.ai.provider.ollama.config.OllamaProperties;
import io.qwenbridge.ai.provider.ollama.dto.OllamaChatRequest;
import io.qwenbridge.ai.provider.ollama.dto.OllamaChatResponse;
import io.qwenbridge.ai.provider.ollama.dto.OllamaEmbeddingRequest;
import io.qwenbridge.ai.provider.ollama.dto.OllamaEmbeddingResponse;
import io.qwenbridge.ai.provider.ollama.dto.OllamaStreamingChatResponse;
import io.qwenbridge.operations.metrics.OperationsMetrics;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import reactor.adapter.JdkFlowAdapter;
import reactor.core.publisher.Flux;

@ApplicationScoped
@Slf4j
public class OllamaClient {

  private final OllamaApiClient api;
  private final OllamaProperties properties;
  private final OperationsMetrics metrics;

  @Inject
  public OllamaClient(
      @RestClient OllamaApiClient api, OllamaProperties properties, OperationsMetrics metrics) {
    this.api = api;
    this.properties = properties;
    this.metrics = metrics;
  }

  public OllamaChatResponse chat(OllamaChatRequest request) {
    log.debug("Sending Ollama chat request. model={}", request.model());
    return execute(
        "chat", () -> invoke(() -> api.chat(request)), "Ollama chat response was empty");
  }

  public Flux<OllamaStreamingChatResponse> streamChat(OllamaChatRequest request) {
    log.debug("Sending Ollama streaming chat request. model={}", request.model());
    long started = System.nanoTime();

    return Flux.defer(() -> JdkFlowAdapter.flowPublisherToFlux(api.streamChat(request)))
        .timeout(properties.readTimeout())
        .doOnComplete(() -> recordProvider("stream", "success", started))
        .doOnError(throwable -> recordProvider("stream", "failure", started))
        .onErrorMap(this::mapStreamingError);
  }

  public OllamaEmbeddingResponse embed(OllamaEmbeddingRequest request) {
    log.debug("Sending Ollama embedding request. model={}", request.model());
    return execute(
        "embedding",
        () -> invoke(() -> api.embed(request)),
        "Ollama embedding response was empty");
  }

  private <T> T execute(String operation, Supplier<T> call, String emptyResponseMessage) {
    int maxRetries = Math.max(0, properties.retryCount());
    long started = System.nanoTime();
    RuntimeException last = null;

    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        T result = call.get();
        if (result == null) {
          throw new AIException(emptyResponseMessage);
        }
        recordProvider(operation, "success", started);
        return result;
      } catch (RuntimeException exception) {
        last = exception;
        if (attempt >= maxRetries || !isRetryable(exception)) {
          recordProvider(operation, "failure", started);
          throw normalize(operation, exception);
        }
        log.warn(
            "Retrying Ollama {} request. attempt={} maxAttempts={} reason={}",
            operation,
            attempt + 1,
            maxRetries,
            exception.getMessage());
      }
    }

    recordProvider(operation, "failure", started);
    throw new AIException(
        "Ollama %s request failed after %d retry attempt(s)".formatted(operation, maxRetries), last);
  }

  private AIException normalize(String operation, RuntimeException exception) {
    if (exception instanceof AIException aiException) {
      return aiException;
    }
    return new AIException("Ollama %s request failed".formatted(operation), exception);
  }

  /**
   * Invokes a REST Client call and translates transport failures into {@link AIException}, keeping
   * the historical {@code "Ollama request failed"} error contract.
   */
  private <T> T invoke(Supplier<T> call) {
    try {
      return call.get();
    } catch (WebApplicationException exception) {
      throw new AIException(describeHttpFailure(exception));
    } catch (ProcessingException exception) {
      throw new AIException("Ollama request failed", exception);
    }
  }

  private Throwable mapStreamingError(Throwable throwable) {
    if (throwable instanceof AIException aiException) {
      return aiException;
    }
    if (throwable instanceof WebApplicationException webApplicationException) {
      return new AIException(describeHttpFailure(webApplicationException));
    }
    return new AIException("Ollama streaming chat request failed", throwable);
  }

  private String describeHttpFailure(WebApplicationException exception) {
    try (Response response = exception.getResponse()) {
      String body =
          response != null && response.hasEntity() ? response.readEntity(String.class) : "";
      int status = response != null ? response.getStatus() : -1;
      return "Ollama request failed. status=%s body=%s".formatted(status, body);
    }
  }

  private boolean isRetryable(Throwable throwable) {
    return !(throwable instanceof IllegalArgumentException);
  }

  private void recordProvider(String operation, String outcome, long started) {
    metrics.recordAiProvider(
        "ollama", operation, outcome, Duration.ofNanos(Math.max(0, System.nanoTime() - started)));
  }
}

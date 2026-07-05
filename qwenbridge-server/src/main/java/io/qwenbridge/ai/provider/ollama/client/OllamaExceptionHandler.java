package io.qwenbridge.ai.provider.ollama.client;

import io.qwenbridge.ai.exception.AIException;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

public final class OllamaExceptionHandler {

  private OllamaExceptionHandler() {}

  public static Mono<? extends Throwable> mapError(ClientResponse response) {
    return response
        .bodyToMono(String.class)
        .defaultIfEmpty("")
        .map(
            body ->
                new AIException(
                    "Ollama request failed. status=%s body=%s"
                        .formatted(response.statusCode(), body)));
  }
}

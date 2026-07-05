package io.qwenbridge.ai.provider.ollama.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qwenbridge.ai.ollama")
public record OllamaProperties(
    URI baseUrl,
    String chatModel,
    String embeddingModel,
    Duration connectTimeout,
    Duration readTimeout,
    int retryCount,
    boolean streamingEnabled) {
  public OllamaProperties {
    if (baseUrl == null) {
      baseUrl = URI.create("http://localhost:11434");
    }

    if (chatModel == null || chatModel.isBlank()) {
      chatModel = "qwen2.5";
    }

    if (embeddingModel == null || embeddingModel.isBlank()) {
      embeddingModel = "bge-m3";
    }

    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(5);
    }

    if (readTimeout == null) {
      readTimeout = Duration.ofSeconds(60);
    }

    if (retryCount < 0) {
      retryCount = 0;
    }
  }
}

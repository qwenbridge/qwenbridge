package io.qwenbridge.ai.provider.ollama.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "qwenbridge.ai.ollama")
public record OllamaProperties(
        URI baseUrl,
        String chatModel,
        String embeddingModel,
        Duration connectTimeout,
        Duration readTimeout
) {
}

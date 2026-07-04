package io.qwenbridge.operations.health;

import io.qwenbridge.ai.provider.ollama.config.OllamaProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class OllamaDependencyHealthChecker implements DependencyHealthChecker {

    private final WebClient webClient;
    private final OllamaProperties properties;

    public OllamaDependencyHealthChecker(OllamaProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.builder().baseUrl(properties.baseUrl().toString()).build();
    }

    @Override
    public DependencyHealth check() {
        long started = System.nanoTime();
        try {
            webClient.get().uri("/api/tags").retrieve().toBodilessEntity().block(properties.connectTimeout().plus(properties.readTimeout()));
            return DependencyHealth.up("ollama", durationMs(started));
        } catch (Exception ex) {
            return DependencyHealth.degraded("ollama", "unavailable", durationMs(started));
        }
    }

    private long durationMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }
}

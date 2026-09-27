package io.qwenbridge.operations.health;

import io.qwenbridge.ai.provider.ollama.client.OllamaApiClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class OllamaDependencyHealthChecker implements DependencyHealthChecker {

  private final OllamaApiClient api;

  @Inject
  public OllamaDependencyHealthChecker(@RestClient OllamaApiClient api) {
    this.api = api;
  }

  @Override
  public DependencyHealth check() {
    long started = System.nanoTime();
    try (Response response = api.tags()) {
      if (response.getStatus() >= 400) {
        return DependencyHealth.degraded("ollama", "unavailable", durationMs(started));
      }
      return DependencyHealth.up("ollama", durationMs(started));
    } catch (Exception ex) {
      return DependencyHealth.degraded("ollama", "unavailable", durationMs(started));
    }
  }

  private long durationMs(long started) {
    return Math.max(0, (System.nanoTime() - started) / 1_000_000);
  }
}

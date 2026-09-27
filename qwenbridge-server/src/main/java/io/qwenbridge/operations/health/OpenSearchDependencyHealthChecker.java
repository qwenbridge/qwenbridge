package io.qwenbridge.operations.health;

import io.qwenbridge.execution.provider.opensearch.client.OpenSearchClient;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OpenSearchDependencyHealthChecker implements DependencyHealthChecker {

  private final OpenSearchClient client;

  public OpenSearchDependencyHealthChecker(OpenSearchClient client) {
    this.client = client;
  }

  @Override
  public DependencyHealth check() {
    long started = System.nanoTime();
    try {
      client.ping();
      return DependencyHealth.up("opensearch", durationMs(started));
    } catch (Exception ex) {
      return DependencyHealth.degraded("opensearch", "unavailable", durationMs(started));
    }
  }

  private long durationMs(long started) {
    return Math.max(0, (System.nanoTime() - started) / 1_000_000);
  }
}

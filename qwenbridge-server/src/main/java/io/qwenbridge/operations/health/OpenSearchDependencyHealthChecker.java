package io.qwenbridge.operations.health;

import io.qwenbridge.execution.provider.opensearch.client.OpenSearchClient;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Readiness of the optional OpenSearch backend. QB-287: it is probed and reported only when
 * OpenSearch is the selected v1 search backend or Commerce backend; the default offline runtime
 * never contacts it.
 */
@ApplicationScoped
public class OpenSearchDependencyHealthChecker implements DependencyHealthChecker {

  private final OpenSearchClient client;
  private final boolean selected;

  @jakarta.inject.Inject
  public OpenSearchDependencyHealthChecker(
      OpenSearchClient client,
      @org.eclipse.microprofile.config.inject.ConfigProperty(
              name = "qwenbridge.search.backend",
              defaultValue = "in-memory")
          String searchBackend,
      @org.eclipse.microprofile.config.inject.ConfigProperty(
              name = "qwenbridge.commerce.backend",
              defaultValue = "memory")
          String commerceBackend) {
    this.client = client;
    this.selected = "opensearch".equals(searchBackend) || "opensearch".equals(commerceBackend);
  }

  @Override
  public boolean active() {
    return selected;
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

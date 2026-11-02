package io.qwenbridge.core.connector;

import io.qwenbridge.core.retrieval.SearchProvider;
import java.util.Optional;

/**
 * The typed connector SPI (QB-153). A connector declares its configuration schema and capabilities,
 * starts from a validated configuration with resolved secrets, reports its health and stops.
 * Retrieval goes through the unified {@link SearchProvider} it exposes; the {@link
 * ConnectorRuntime} owns lifecycle, access checks and resilience.
 */
public interface Connector {

  String id();

  ConfigSchema configSchema();

  ConnectorCapabilities capabilities();

  /** Connects. Throws on failure; the runtime records FAILED with the reason. */
  void start(ConnectorConfig config, SecretResolver secrets) throws Exception;

  /** A cheap health check; false means DEGRADED. */
  default boolean healthy() {
    return true;
  }

  default void stop() {}

  default Optional<SearchProvider> search() {
    return Optional.empty();
  }
}

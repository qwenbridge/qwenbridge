package io.qwenbridge.catalog.connector;

import java.net.URI;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Creates catalog connectors of one kind (QB-204). Optional connector modules register an
 * implementation with {@link ServiceLoader}; the server knows none of them.
 */
public interface CatalogConnectorFactory {

  /** The {@code qwenbridge.catalog.kind} value this factory serves, e.g. {@code http-json}. */
  String kind();

  /**
   * @param endpoint operator-configured source URL
   * @param token operator-configured credential (may be empty)
   */
  CatalogConnector open(URI endpoint, String token);

  /** The factory for {@code kind} among the connector modules on the class path. */
  static Optional<CatalogConnectorFactory> find(String kind) {
    for (CatalogConnectorFactory factory : ServiceLoader.load(CatalogConnectorFactory.class))
      if (factory.kind().equals(kind)) return Optional.of(factory);
    return Optional.empty();
  }
}

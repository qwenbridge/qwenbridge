package io.qwenbridge.core.connector.pack;

import io.qwenbridge.core.connector.Connector;

/**
 * A distributable Connector Pack (QB-237), found with {@link java.util.ServiceLoader}. It does not
 * replace the Connector SPI: it describes a set of {@link Connector}s and creates them only when
 * the pack is enabled, so a disabled pack never constructs a client or asks for credentials.
 */
public interface ConnectorPackProvider {

  ConnectorPackManifest manifest();

  /** A new, unstarted connector for one of the manifest's declarations. */
  Connector create(String connectorId);
}

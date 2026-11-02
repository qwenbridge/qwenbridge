package io.qwenbridge.extensions.testpack;

import io.qwenbridge.core.connector.ConfigSchema;
import io.qwenbridge.core.connector.Connector;
import io.qwenbridge.core.connector.ConnectorCapabilities;
import io.qwenbridge.core.connector.ConnectorConfig;
import io.qwenbridge.core.connector.SecretResolver;
import io.qwenbridge.core.connector.pack.ConnectorPackManifest;
import io.qwenbridge.core.connector.pack.ConnectorPackProvider;
import io.qwenbridge.core.retrieval.RetrievalMode;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A test-only Connector Pack (QB-287). QwenBridge 1.0 ships no Connector Pack, so the generic
 * extension mechanism (ServiceLoader discovery, disabled by default, configuration with secret
 * references, failure isolation) is exercised with this pack. Its {@code example-primary} connector
 * reads a settings {@code url} and fails to start when nothing listens there.
 */
public final class ExampleConnectorPack implements ConnectorPackProvider {

  static final ConnectorCapabilities READ_KEYWORD =
      new ConnectorCapabilities(
          EnumSet.of(ConnectorCapabilities.Access.READ), EnumSet.of(RetrievalMode.KEYWORD));
  static final ConnectorCapabilities READ_WRITE =
      new ConnectorCapabilities(
          EnumSet.allOf(ConnectorCapabilities.Access.class), EnumSet.of(RetrievalMode.KEYWORD));

  @Override
  public ConnectorPackManifest manifest() {
    return new ConnectorPackManifest(
        "example",
        "1.0.0",
        "qwenbridge-tests",
        "1.0.0",
        "2.0.0",
        List.of(
            new ConnectorPackManifest.Declaration(
                "example-primary", "example-store", "test-only read connector", READ_KEYWORD),
            new ConnectorPackManifest.Declaration(
                "example-secondary",
                "example-index",
                "test-only read-write connector",
                READ_WRITE)));
  }

  @Override
  public Connector create(String connectorId) {
    return new Connector() {
      @Override
      public String id() {
        return connectorId;
      }

      @Override
      public ConfigSchema configSchema() {
        return new ConfigSchema(Set.of("url"), Set.of(), Set.of(), Set.of("password"));
      }

      @Override
      public ConnectorCapabilities capabilities() {
        return connectorId.equals("example-primary") ? READ_KEYWORD : READ_WRITE;
      }

      @Override
      public void start(ConnectorConfig config, SecretResolver secrets) throws Exception {
        URI url = URI.create(config.settings().get("url"));
        try (var socket = new Socket()) {
          socket.connect(new InetSocketAddress(url.getHost(), url.getPort()), 1_000);
        }
      }
    };
  }
}

package io.qwenbridge.extensions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.core.connector.ConnectorConfig;
import io.qwenbridge.core.connector.EnvironmentSecrets;
import io.qwenbridge.core.connector.SecretRef;
import io.qwenbridge.core.connector.pack.ConnectorPackProvider;
import io.qwenbridge.core.connector.pack.ConnectorPacks;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Optional Connector Packs in the server (QB-237 – QB-242). Packs on the classpath are discovered
 * at startup and stay disabled unless listed in {@code qwenbridge.connector-packs.enabled}; with
 * none enabled (the default) no connector is created and no credential is needed.
 *
 * <p>{@code qwenbridge.connector-packs.config} is a JSON object of connector configurations, keyed
 * by connector id: {@code {"my-connector": {"settings": {...}, "secrets": {"password":
 * "env:MY_CONNECTOR_PASSWORD"}}}}. Secrets are references only ({@code env:} or {@code file:});
 * values are never logged or shown. No Connector Pack ships with QwenBridge 1.0 (QB-287): this is
 * the extension point for separately installed packs, beside the built-in Commerce backends.
 */
@Singleton
public final class ConnectorPackService {

  private static final Logger LOG = Logger.getLogger(ConnectorPackService.class);

  private final ConnectorPacks packs;

  public ConnectorPackService(
      ObjectMapper mapper,
      @ConfigProperty(name = "qwenbridge.connector-packs.enabled") Optional<List<String>> enabled,
      @ConfigProperty(name = "qwenbridge.connector-packs.config", defaultValue = "{}") String json,
      @ConfigProperty(name = "qwenbridge.connector-packs.secrets-dir") Optional<String> secretsDir,
      @ConfigProperty(name = "qwenbridge.connector-packs.timeout", defaultValue = "10s")
          Duration timeout,
      @ConfigProperty(name = "qwenbridge.connector-packs.failure-threshold", defaultValue = "5")
          int failureThreshold,
      @ConfigProperty(name = "qwenbridge.connector-packs.open-for", defaultValue = "30s")
          Duration openFor) {
    packs =
        new ConnectorPacks(
            Clock.systemUTC(), failureThreshold, openFor, timeout, d -> Thread.sleep(d.toMillis()));
    List<ConnectorPackProvider> providers = new ArrayList<>();
    ServiceLoader.load(ConnectorPackProvider.class, ConnectorPackService.class.getClassLoader())
        .stream()
        .forEach(
            p -> {
              try {
                providers.add(p.get());
              } catch (RuntimeException | java.util.ServiceConfigurationError e) {
                LOG.warnf(
                    "Skipped connector pack %s: %s",
                    p.type().getName(), e.getClass().getSimpleName());
              }
            });
    packs.discover(providers);
    Map<String, ConnectorConfig> configs = configurations(mapper, json);
    var secrets = EnvironmentSecrets.system(secretsDir.map(Path::of));
    for (String id : enabled.orElse(List.of())) {
      try {
        Map<String, ConnectorConfig> own = new HashMap<>();
        packs
            .status(id)
            .orElseThrow(() -> new IllegalArgumentException("Unknown connector pack " + id))
            .connectors()
            .forEach(
                c ->
                    Optional.ofNullable(configs.get(c.id()))
                        .ifPresent(cfg -> own.put(c.id(), cfg)));
        var status = packs.enable(id, own, secrets);
        LOG.infof("Connector pack %s enabled: %s (%s)", id, status.state(), status.reason());
      } catch (RuntimeException e) {
        LOG.warnf("Connector pack %s not enabled: %s", id, e.getMessage());
      }
    }
  }

  /** Parses connector configurations; a malformed document is refused at startup. */
  static Map<String, ConnectorConfig> configurations(ObjectMapper mapper, String json) {
    JsonNode root;
    try {
      root = mapper.readTree(json);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalArgumentException("qwenbridge.connector-packs.config is not valid JSON");
    }
    if (!root.isObject())
      throw new IllegalArgumentException("qwenbridge.connector-packs.config must be an object");
    Map<String, ConnectorConfig> out = new LinkedHashMap<>();
    for (var entry : root.properties()) {
      Map<String, String> settings = new LinkedHashMap<>();
      Map<String, SecretRef> secrets = new LinkedHashMap<>();
      entry
          .getValue()
          .path("settings")
          .properties()
          .forEach(s -> settings.put(s.getKey(), s.getValue().asText()));
      for (var s : entry.getValue().path("secrets").properties()) {
        if (!SecretRef.isReference(s.getValue().asText()))
          throw new IllegalArgumentException(
              "Secret "
                  + s.getKey()
                  + " of connector "
                  + entry.getKey()
                  + " must be an env: or file: reference");
        secrets.put(s.getKey(), new SecretRef(s.getValue().asText()));
      }
      out.put(
          entry.getKey(),
          new ConnectorConfig(
              ConnectorConfig.VERSION,
              entry.getKey(),
              entry.getValue().path("enabled").asBoolean(true),
              settings,
              secrets));
    }
    return out;
  }

  public ConnectorPacks packs() {
    return packs;
  }

  public List<ConnectorPacks.PackStatus> statuses() {
    return packs.statuses();
  }

  @PreDestroy
  void close() {
    packs.close();
  }
}

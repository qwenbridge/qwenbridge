package io.qwenbridge.core.connector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The versioned connector configuration contract (QB-155, QB-156). Settings are plain values;
 * secrets are references only. A disabled connector needs no settings at all.
 */
public record ConnectorConfig(
    String version,
    String connectorId,
    boolean enabled,
    Map<String, String> settings,
    Map<String, SecretRef> secrets) {

  public static final String VERSION = "connector-config/1";

  public ConnectorConfig {
    Objects.requireNonNull(version, "version must not be null");
    Objects.requireNonNull(connectorId, "connectorId must not be null");
    settings = settings == null ? Map.of() : Map.copyOf(settings);
    secrets = secrets == null ? Map.of() : Map.copyOf(secrets);
  }

  public static ConnectorConfig disabled(String connectorId) {
    return new ConnectorConfig(VERSION, connectorId, false, Map.of(), Map.of());
  }

  /** Problems with this configuration against a schema; empty when valid. Never shows values. */
  public List<String> problems(ConfigSchema schema) {
    List<String> out = new ArrayList<>();
    if (!VERSION.equals(version)) {
      out.add("unsupported configuration version " + version);
    }
    if (!enabled) {
      return out;
    }
    for (String key : new TreeMap<>(settings).keySet()) {
      if (schema.secret(key)) {
        out.add("secret " + key + " must be a reference (env: or file:), not a value");
      } else if (!schema.setting(key)) {
        out.add("unknown setting " + key);
      }
    }
    for (String key : new TreeMap<>(secrets).keySet()) {
      if (!schema.secret(key)) {
        out.add("unknown secret " + key);
      }
    }
    for (String key : schema.requiredSettings().stream().sorted().toList()) {
      if (!settings.containsKey(key) || settings.get(key).isBlank()) {
        out.add("missing setting " + key);
      }
    }
    for (String key : schema.requiredSecrets().stream().sorted().toList()) {
      if (!secrets.containsKey(key)) {
        out.add("missing secret " + key);
      }
    }
    return out;
  }

  @Override
  public String toString() {
    return "ConnectorConfig["
        + connectorId
        + ", "
        + version
        + ", enabled="
        + enabled
        + ", settings="
        + new TreeMap<>(settings).keySet()
        + ", secrets="
        + new TreeMap<>(secrets).keySet()
        + "]";
  }
}

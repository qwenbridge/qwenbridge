package io.qwenbridge.core.connector;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The connectors of a deployment, isolated from one another (QB-153, QB-159): each starts on its
 * own, so a missing, invalid or failing connector leaves the others working.
 */
public final class ConnectorRegistry {
  private final Map<String, ConnectorRuntime> runtimes = new LinkedHashMap<>();

  public synchronized void register(ConnectorRuntime runtime) {
    if (runtimes.putIfAbsent(runtime.id(), runtime) != null) {
      throw new IllegalArgumentException("duplicate connector " + runtime.id());
    }
  }

  /** Starts every connector with its configuration (disabled when none is given). */
  public synchronized Map<String, ConnectorState> startAll(
      Map<String, ConnectorConfig> configs, SecretResolver secrets) {
    Map<String, ConnectorState> out = new LinkedHashMap<>();
    for (var runtime : runtimes.values()) {
      ConnectorConfig config =
          configs.getOrDefault(runtime.id(), ConnectorConfig.disabled(runtime.id()));
      out.put(runtime.id(), runtime.start(config, secrets));
    }
    return out;
  }

  public synchronized Optional<ConnectorRuntime> get(String id) {
    return Optional.ofNullable(runtimes.get(id));
  }

  public synchronized List<ConnectorRuntime> all() {
    return List.copyOf(runtimes.values());
  }

  public synchronized void stopAll() {
    runtimes.values().forEach(ConnectorRuntime::stop);
  }
}

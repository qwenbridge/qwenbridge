package io.qwenbridge.plugin;

import java.util.Map;
import java.util.Set;

/** Java 25 extension contract; implementations are administrator-trusted executable code. */
public interface QwenBridgePlugin extends AutoCloseable {
  record Manifest(String id, String version, int apiVersion, Set<String> capabilities) {
    public Manifest {
      if (id == null
          || !id.matches("[a-z][a-z0-9.-]{0,63}")
          || version == null
          || !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")
          || apiVersion != 1) throw new IllegalArgumentException("Incompatible plugin manifest");
      capabilities = Set.copyOf(capabilities);
      if (capabilities.isEmpty())
        throw new IllegalArgumentException("Plugin must declare capabilities");
    }
  }

  Manifest manifest();

  Map<String, Object> invoke(String capability, Map<String, Object> input);

  @Override
  default void close() {}
}

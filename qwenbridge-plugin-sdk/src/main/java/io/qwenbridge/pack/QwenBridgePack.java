package io.qwenbridge.pack;

import java.util.Map;

/** Packs execute trusted publisher code; signatures do not constitute a code sandbox. */
public interface QwenBridgePack extends AutoCloseable {
  PackManifest manifest();

  record Context(String tenant, Map<String, String> configuration, SecretResolver secrets) {
    public Context {
      configuration = Map.copyOf(configuration);
    }
  }

  @FunctionalInterface
  interface SecretResolver {
    char[] resolve(String reference);
  }

  default void start(Context context) {}

  @Override
  default void close() {}
}

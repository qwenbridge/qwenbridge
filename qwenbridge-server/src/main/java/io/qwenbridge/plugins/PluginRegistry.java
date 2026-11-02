package io.qwenbridge.plugins;

import io.qwenbridge.plugin.QwenBridgePlugin;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public final class PluginRegistry {
  private final ConcurrentHashMap<String, QwenBridgePlugin> plugins = new ConcurrentHashMap<>();
  private final URLClassLoader loader;

  @jakarta.inject.Inject
  public PluginRegistry(
      @ConfigProperty(name = "qwenbridge.plugins.directory") java.util.Optional<String> directory) {
    this(directory.orElse(""));
  }

  public PluginRegistry(String directory) {
    try {
      var urls = new java.util.ArrayList<java.net.URL>();
      if (!directory.isBlank()) {
        Path root = Path.of(directory).toRealPath();
        try (var files = Files.list(root)) {
          for (Path file :
              files
                  .filter(
                      p -> {
                        var name = p.getFileName();
                        return name != null && name.toString().endsWith(".jar");
                      })
                  .sorted()
                  .toList()) {
            if (!file.toRealPath().startsWith(root))
              throw new IllegalArgumentException("Plugin path escapes configured directory");
            urls.add(file.toUri().toURL());
          }
        }
      }
      loader =
          new URLClassLoader(
              urls.toArray(java.net.URL[]::new), QwenBridgePlugin.class.getClassLoader());
      try {
        for (QwenBridgePlugin plugin : ServiceLoader.load(QwenBridgePlugin.class, loader)) {
          var manifest = plugin.manifest();
          if (manifest.apiVersion() != 1 || plugins.putIfAbsent(manifest.id(), plugin) != null) {
            plugin.close();
            throw new IllegalArgumentException("Duplicate or incompatible plugin");
          }
        }
      } catch (RuntimeException | java.util.ServiceConfigurationError ex) {
        try {
          close();
        } catch (RuntimeException cleanup) {
          ex.addSuppressed(cleanup);
        }
        throw ex;
      }
    } catch (IOException ex) {
      throw new IllegalArgumentException("Cannot load plugin directory", ex);
    }
  }

  public List<QwenBridgePlugin.Manifest> manifests() {
    return plugins.values().stream().map(QwenBridgePlugin::manifest).toList();
  }

  /**
   * Invokes a plugin capability on behalf of a caller (QB-076). The call is authorized by the
   * central policy for the operation it serves, with the plugin as the connector, before the plugin
   * runs; there is no invocation path without a caller.
   */
  public Map<String, Object> invoke(
      io.qwenbridge.security.ApiIdentity.Principal caller,
      String operation,
      String id,
      String capability,
      Map<String, Object> input) {
    java.util.Objects.requireNonNull(caller, "caller");
    caller.require(
        operation, io.qwenbridge.core.policy.Resource.of("plugin", caller.tenant()).via(id));
    var plugin = plugins.get(id);
    if (plugin == null || !plugin.manifest().capabilities().contains(capability))
      throw new IllegalArgumentException("Plugin capability is unavailable");
    return Map.copyOf(plugin.invoke(capability, Map.copyOf(input)));
  }

  @PreDestroy
  public void close() {
    plugins
        .values()
        .forEach(
            plugin -> {
              try {
                plugin.close();
              } catch (RuntimeException ignored) {
                /* Close remaining plugins. */
              }
            });
    plugins.clear();
    try {
      loader.close();
    } catch (IOException ex) {
      throw new IllegalStateException("Cannot close plugin loader", ex);
    }
  }
}

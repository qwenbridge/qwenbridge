package io.qwenbridge.extensions;

import io.qwenbridge.agent.Agent;
import io.qwenbridge.agent.AgentDefinition;
import io.qwenbridge.agent.ToolDefinition;
import io.qwenbridge.agent.ToolHandler;
import io.qwenbridge.plugin.Plugin;
import io.qwenbridge.plugin.PluginContext;
import io.qwenbridge.plugin.PluginManifest;
import io.qwenbridge.plugin.PluginSdk;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Plugin SDK 1.0 host (QB-225, QB-228 – QB-230).
 *
 * <ol>
 *   <li><b>Discover</b>: every jar in {@code qwenbridge.plugins.directory} with a {@value
 *       PluginSdk#MANIFEST_ENTRY}; the manifest is parsed as data, no class is loaded.
 *   <li><b>Validate</b>: SDK and runtime ranges, dependencies, and identity. Two jars with the same
 *       plugin id are both rejected (deterministic, independent of file order).
 *   <li><b>Activate</b>: in dependency order, load the entry point, check it implements {@link
 *       Plugin}, and call {@code start} with a time limit. A plugin may register only the tools and
 *       agents its manifest declares. Any failure rolls back its registrations and marks it FAILED;
 *       the rest of the server is unaffected.
 * </ol>
 *
 * Repeated activation of a started plugin is a no-op, so registrations are never duplicated.
 */
@jakarta.inject.Singleton
public final class PluginHost {
  private static final Logger LOG = Logger.getLogger(PluginHost.class);

  public enum State {
    DISCOVERED,
    REJECTED,
    STARTED,
    STOPPED,
    FAILED
  }

  public record Status(
      String id,
      String version,
      String publisher,
      State state,
      String reason,
      List<String> tools,
      List<String> agents) {
    public Status {
      tools = List.copyOf(tools);
      agents = List.copyOf(agents);
    }
  }

  private static final class Entry {
    final PluginManifest manifest;
    final Supplier<Plugin> factory;
    final URLClassLoader loader;
    State state = State.DISCOVERED;
    String reason = "";
    Plugin instance;

    Entry(PluginManifest manifest, Supplier<Plugin> factory, URLClassLoader loader) {
      this.manifest = manifest;
      this.factory = factory;
      this.loader = loader;
    }
  }

  private final ExtensionRegistry registry;
  private final String runtimeVersion;
  private final Duration startTimeout;
  private final Map<String, Entry> plugins = new LinkedHashMap<>();
  private final List<Status> rejected = new ArrayList<>();

  @jakarta.inject.Inject
  public PluginHost(
      ExtensionRegistry registry,
      @ConfigProperty(name = "qwenbridge.version") String runtimeVersion,
      @ConfigProperty(name = "qwenbridge.plugins.directory") Optional<String> directory,
      @ConfigProperty(name = "qwenbridge.plugins.start-timeout", defaultValue = "10s")
          Duration startTimeout) {
    this.registry = registry;
    this.runtimeVersion = runtimeVersion.replaceAll("[^0-9.].*$", "");
    this.startTimeout = startTimeout;
    directory.filter(d -> !d.isBlank()).ifPresent(this::discover);
    activateAll();
  }

  /** For tests: a host without a directory. */
  public PluginHost(ExtensionRegistry registry, String runtimeVersion, Duration startTimeout) {
    this.registry = registry;
    this.runtimeVersion = runtimeVersion;
    this.startTimeout = startTimeout;
  }

  /** Scans a directory for Plugin SDK jars and validates their manifests (no plugin code runs). */
  private static String fileName(Path path) {
    Path name = path.getFileName();
    return name == null ? path.toString() : name.toString();
  }

  public synchronized void discover(String directory) {
    Map<String, List<Path>> byId = new LinkedHashMap<>();
    Map<Path, PluginManifest> manifests = new LinkedHashMap<>();
    try {
      Path root = Path.of(directory).toRealPath();
      try (var files = Files.list(root)) {
        for (Path jar : files.filter(p -> fileName(p).endsWith(".jar")).sorted().toList()) {
          if (!jar.toRealPath().startsWith(root)) {
            rejected.add(
                new Status(
                    fileName(jar),
                    "",
                    "",
                    State.REJECTED,
                    "path escapes the plugin directory",
                    List.of(),
                    List.of()));
            continue;
          }
          try {
            var manifest = PluginManifests.read(jar);
            if (manifest.isEmpty()) continue; // a legacy QwenBridgePlugin jar; not handled here
            manifests.put(jar, manifest.get());
            byId.computeIfAbsent(manifest.get().id(), k -> new ArrayList<>()).add(jar);
          } catch (IOException | RuntimeException e) {
            rejected.add(
                new Status(
                    fileName(jar),
                    "",
                    "",
                    State.REJECTED,
                    "invalid manifest: " + e.getMessage(),
                    List.of(),
                    List.of()));
          }
        }
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot read plugin directory", e);
    }
    byId.forEach(
        (id, jars) -> {
          PluginManifest manifest = manifests.get(jars.getFirst());
          if (jars.size() > 1 || plugins.containsKey(id)) {
            rejected.add(
                new Status(
                    id,
                    manifest.version(),
                    manifest.publisher(),
                    State.REJECTED,
                    "duplicate plugin id in " + jars.size() + " jars",
                    manifest.tools().stream().sorted().toList(),
                    manifest.agents().stream().sorted().toList()));
            return;
          }
          try {
            var loader =
                new URLClassLoader(
                    new java.net.URL[] {jars.getFirst().toUri().toURL()},
                    Plugin.class.getClassLoader());
            plugins.put(id, new Entry(manifest, () -> instantiate(loader, manifest), loader));
          } catch (IOException e) {
            rejected.add(
                new Status(
                    id,
                    manifest.version(),
                    manifest.publisher(),
                    State.REJECTED,
                    "cannot open jar",
                    List.of(),
                    List.of()));
          }
        });
  }

  private static Plugin instantiate(ClassLoader loader, PluginManifest manifest) {
    try {
      Class<?> type = Class.forName(manifest.entryPoint(), false, loader);
      if (!Plugin.class.isAssignableFrom(type))
        throw new IllegalArgumentException(
            "Entry point does not implement io.qwenbridge.plugin.Plugin");
      return (Plugin) type.getDeclaredConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalArgumentException(
          "Cannot instantiate " + manifest.entryPoint() + ": " + e.getClass().getSimpleName());
    }
  }

  /** A trusted plugin supplied by the application or a test (same validation, no jar). */
  public synchronized void install(PluginManifest manifest, Supplier<Plugin> factory) {
    if (plugins.containsKey(manifest.id()))
      throw new IllegalArgumentException("Plugin " + manifest.id() + " is already installed");
    plugins.put(manifest.id(), new Entry(manifest, factory, null));
  }

  /** Activates every discovered or stopped plugin, dependencies first. */
  public synchronized void activateAll() {
    for (String id : List.copyOf(plugins.keySet())) activate(id, new java.util.HashSet<>());
  }

  public synchronized Status activate(String id) {
    return activate(id, new java.util.HashSet<>());
  }

  private Status activate(String id, java.util.Set<String> visiting) {
    Entry entry = plugins.get(id);
    if (entry == null) throw new IllegalArgumentException("Unknown plugin " + id);
    if (entry.state == State.STARTED || entry.state == State.REJECTED) return status(entry);
    var m = entry.manifest;
    if (!m.compatibleWith(PluginSdk.VERSION, runtimeVersion))
      return reject(
          entry,
          "incompatible: requires SDK "
              + m.sdk()
              + " and runtime "
              + m.runtime()
              + ", have SDK "
              + PluginSdk.VERSION
              + " and runtime "
              + runtimeVersion);
    if (!visiting.add(id)) return fail(entry, "dependency cycle");
    for (var dependency : m.dependencies()) {
      Entry dep = plugins.get(dependency.id());
      if (dep == null) return fail(entry, "missing dependency " + dependency.id());
      if (!dependency.accepts(dep.manifest.version()))
        return fail(
            entry,
            "dependency "
                + dependency.id()
                + " "
                + dep.manifest.version()
                + " is outside the required range");
      if (activate(dependency.id(), visiting).state() != State.STARTED)
        return fail(entry, "dependency " + dependency.id() + " is not running");
    }
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var future =
          executor.submit(
              () -> {
                Plugin plugin = entry.factory.get();
                plugin.start(context(entry));
                return plugin;
              });
      entry.instance = future.get(startTimeout.toMillis(), TimeUnit.MILLISECONDS);
      entry.state = State.STARTED;
      entry.reason = "";
      LOG.infof("Plugin %s %s started (%d extensions)", id, m.version(), registry.count(id));
    } catch (TimeoutException e) {
      return fail(entry, "start timed out after " + startTimeout.toMillis() + " ms");
    } catch (ExecutionException e) {
      return fail(entry, "start failed: " + safe(e.getCause()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return fail(entry, "start interrupted");
    } catch (RuntimeException e) {
      return fail(entry, "start failed: " + safe(e));
    }
    return status(entry);
  }

  private PluginContext context(Entry entry) {
    var manifest = entry.manifest;
    var registrar =
        new PluginContext.Registrar() {
          public void tool(ToolDefinition definition, ToolHandler handler) {
            if (!manifest.tools().contains(definition.id()))
              throw new IllegalArgumentException(
                  "Tool " + definition.id() + " is not declared in the manifest");
            registry.addTool(manifest.id(), definition, java.util.Objects.requireNonNull(handler));
          }

          public void agent(AgentDefinition definition, Agent agent) {
            if (!manifest.agents().contains(definition.id()))
              throw new IllegalArgumentException(
                  "Agent " + definition.id() + " is not declared in the manifest");
            registry.addAgent(manifest.id(), definition, java.util.Objects.requireNonNull(agent));
          }
        };
    return new PluginContext() {
      public String pluginId() {
        return manifest.id();
      }

      public Map<String, String> configuration() {
        return Map.of();
      }

      public Registrar registrar() {
        return registrar;
      }
    };
  }

  /** Stops a plugin: its registrations are removed first, then {@code stop()} runs (bounded). */
  public synchronized Status stop(String id) {
    Entry entry = plugins.get(id);
    if (entry == null) throw new IllegalArgumentException("Unknown plugin " + id);
    if (entry.state != State.STARTED) return status(entry);
    registry.removePlugin(id);
    Plugin instance = entry.instance;
    entry.instance = null;
    entry.state = State.STOPPED;
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      executor
          .submit(
              () -> {
                instance.stop();
                return null;
              })
          .get(startTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      entry.reason =
          "stop did not complete cleanly: "
              + safe(e instanceof ExecutionException ? e.getCause() : e);
    }
    return status(entry);
  }

  private Status fail(Entry entry, String reason) {
    registry.removePlugin(entry.manifest.id());
    entry.state = State.FAILED;
    entry.reason = reason;
    entry.instance = null;
    LOG.warnf("Plugin %s failed: %s", entry.manifest.id(), reason);
    return status(entry);
  }

  private Status reject(Entry entry, String reason) {
    entry.state = State.REJECTED;
    entry.reason = reason;
    LOG.warnf("Plugin %s rejected: %s", entry.manifest.id(), reason);
    return status(entry);
  }

  private static String safe(Throwable t) {
    String message = t == null ? "" : t.getMessage();
    return t == null
        ? "unknown"
        : t.getClass().getSimpleName()
            + (message == null
                ? ""
                : ": "
                    + io.qwenbridge.core.security.Sanitizer.forSink(
                        io.qwenbridge.core.security.Sanitizer.Sink.LOG, message));
  }

  private Status status(Entry e) {
    return new Status(
        e.manifest.id(),
        e.manifest.version(),
        e.manifest.publisher(),
        e.state,
        e.reason,
        e.manifest.tools().stream().sorted().toList(),
        e.manifest.agents().stream().sorted().toList());
  }

  public synchronized List<Status> statuses() {
    var all = new ArrayList<Status>();
    plugins.values().forEach(e -> all.add(status(e)));
    all.addAll(rejected);
    return all;
  }

  @PreDestroy
  public synchronized void close() {
    for (String id : List.copyOf(plugins.keySet())) stop(id);
    for (Entry e : plugins.values())
      if (e.loader != null)
        try {
          e.loader.close();
        } catch (IOException ignored) {
          // best effort
        }
  }
}

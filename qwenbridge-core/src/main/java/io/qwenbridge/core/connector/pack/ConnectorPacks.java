package io.qwenbridge.core.connector.pack;

import io.qwenbridge.core.connector.CircuitBreaker;
import io.qwenbridge.core.connector.ConfigSchema;
import io.qwenbridge.core.connector.Connector;
import io.qwenbridge.core.connector.ConnectorCapabilities;
import io.qwenbridge.core.connector.ConnectorConfig;
import io.qwenbridge.core.connector.ConnectorRuntime;
import io.qwenbridge.core.connector.ConnectorState;
import io.qwenbridge.core.connector.Resilient;
import io.qwenbridge.core.connector.SecretResolver;
import io.qwenbridge.core.retrieval.SearchProvider;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Optional Connector Packs (QB-237 – QB-242). Packs are discovered as data (their manifests) and
 * stay DISABLED until enabled with configurations: a disabled pack never creates a connector, so it
 * builds no client and needs no credentials. Each enabled connector runs in its own {@link
 * ConnectorRuntime} with its own timeout and circuit breaker, so one pack's failure (bad
 * credentials, an outage, timeouts, an open circuit, a shutdown) never changes another's state.
 *
 * <p>A connector can never do more than its pack declared: capabilities are the intersection of the
 * declaration and what the started connector reports, and a connector reporting more fails to
 * start.
 */
public final class ConnectorPacks {

  /** Aggregate pack state. IDLE: enabled, but no connector of the pack is configured. */
  public enum PackState {
    DISABLED,
    REJECTED,
    IDLE,
    READY,
    DEGRADED,
    FAILED
  }

  public record ConnectorView(
      String id,
      String system,
      String state,
      String reason,
      ConnectorCapabilities declared,
      Optional<ConnectorCapabilities> effective,
      Optional<String> circuit,
      List<String> settings,
      List<String> secrets) {
    public ConnectorView {
      settings = List.copyOf(settings);
      secrets = List.copyOf(secrets);
    }
  }

  public record PackStatus(
      String id,
      String version,
      String publisher,
      List<String> systems,
      String spi,
      PackState state,
      String reason,
      List<ConnectorView> connectors) {
    public PackStatus {
      systems = List.copyOf(systems);
      connectors = List.copyOf(connectors);
    }
  }

  /** Thrown when a started connector reports more than its pack declared. */
  static final class CapabilityViolation extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    CapabilityViolation() {
      super("capabilities exceed the pack declaration");
    }
  }

  private record Enabled(
      ConnectorPackManifest.Declaration declaration,
      ConnectorRuntime runtime,
      ConnectorConfig config) {}

  private static final class Pack {
    final ConnectorPackManifest manifest;
    final ConnectorPackProvider provider;
    final Map<String, Enabled> connectors = new LinkedHashMap<>();
    boolean enabled;

    Pack(ConnectorPackManifest manifest, ConnectorPackProvider provider) {
      this.manifest = manifest;
      this.provider = provider;
    }
  }

  private final Clock clock;
  private final int failureThreshold;
  private final Duration openFor;
  private final Duration timeout;
  private final Resilient.Sleeper sleeper;
  private final Map<String, Pack> packs = new TreeMap<>();
  private final List<PackStatus> rejected = new ArrayList<>();

  public ConnectorPacks(
      Clock clock,
      int failureThreshold,
      Duration openFor,
      Duration timeout,
      Resilient.Sleeper sleeper) {
    this.clock = clock;
    this.failureThreshold = failureThreshold;
    this.openFor = openFor;
    this.timeout = timeout;
    this.sleeper = sleeper;
  }

  /**
   * Reads every provider's manifest. Invalid manifests, packs for another Connector SPI and
   * duplicate pack or connector ids are rejected (every claimant of a duplicate id); nothing is
   * created.
   */
  public synchronized void discover(Iterable<ConnectorPackProvider> providers) {
    Map<String, List<Pack>> byId = new HashMap<>();
    Map<String, List<String>> connectorOwners = new HashMap<>();
    List<Pack> found = new ArrayList<>();
    for (ConnectorPackProvider provider : providers) {
      ConnectorPackManifest manifest;
      try {
        manifest = provider.manifest();
        if (manifest == null) throw new IllegalArgumentException("no manifest");
      } catch (RuntimeException e) {
        rejected.add(rejection(provider.getClass().getName(), "invalid manifest"));
        continue;
      }
      if (!manifest.supportsSpi(ConnectorPackManifest.SPI_VERSION)) {
        rejected.add(
            rejection(
                manifest,
                "requires Connector SPI ["
                    + manifest.spiMinimum()
                    + ", "
                    + manifest.spiMaximumExclusive()
                    + "), runtime has "
                    + ConnectorPackManifest.SPI_VERSION));
        continue;
      }
      var pack = new Pack(manifest, provider);
      found.add(pack);
      byId.computeIfAbsent(manifest.id(), k -> new ArrayList<>()).add(pack);
      for (var c : manifest.connectors())
        connectorOwners.computeIfAbsent(c.id(), k -> new ArrayList<>()).add(manifest.id());
    }
    for (var existing : packs.values()) {
      byId.computeIfAbsent(existing.manifest.id(), k -> new ArrayList<>()).add(existing);
      for (var c : existing.manifest.connectors())
        connectorOwners.computeIfAbsent(c.id(), k -> new ArrayList<>()).add(existing.manifest.id());
    }
    for (Pack pack : found) {
      String id = pack.manifest.id();
      if (byId.get(id).size() > 1) {
        rejected.add(rejection(pack.manifest, "duplicate pack id"));
        continue;
      }
      var clash =
          pack.manifest.connectors().stream()
              .filter(c -> connectorOwners.get(c.id()).size() > 1)
              .map(ConnectorPackManifest.Declaration::id)
              .sorted()
              .toList();
      if (!clash.isEmpty()) {
        rejected.add(rejection(pack.manifest, "connector ids claimed by another pack: " + clash));
        continue;
      }
      packs.put(id, pack);
    }
  }

  private static PackStatus rejection(String id, String reason) {
    return new PackStatus(id, "", "", List.of(), "", PackState.REJECTED, reason, List.of());
  }

  private static PackStatus rejection(ConnectorPackManifest m, String reason) {
    return new PackStatus(
        m.id(),
        m.version(),
        m.publisher(),
        m.systems().stream().sorted().toList(),
        spi(m),
        PackState.REJECTED,
        reason,
        List.of());
  }

  private static String spi(ConnectorPackManifest m) {
    return "[" + m.spiMinimum() + ", " + m.spiMaximumExclusive() + ")";
  }

  /**
   * Enables a pack: creates and starts only the connectors that have an enabled configuration.
   * Unknown connector ids are refused before anything is created. Configuration problems and start
   * failures leave that connector FAILED with a reason that never contains values; the pack's other
   * connectors and other packs are unaffected. Re-enabling reconfigures (stops the old connectors
   * first).
   */
  public synchronized PackStatus enable(
      String packId, Map<String, ConnectorConfig> configs, SecretResolver secrets) {
    Pack pack = packs.get(packId);
    if (pack == null) throw new IllegalArgumentException("Unknown connector pack " + packId);
    Map<String, ConnectorPackManifest.Declaration> declared = new LinkedHashMap<>();
    pack.manifest.connectors().forEach(d -> declared.put(d.id(), d));
    var unknown = configs.keySet().stream().filter(k -> !declared.containsKey(k)).sorted().toList();
    if (!unknown.isEmpty())
      throw new IllegalArgumentException("Pack " + packId + " has no connectors " + unknown);
    stopAll(pack);
    pack.enabled = true;
    for (var declaration : declared.values()) {
      ConnectorConfig config = configs.get(declaration.id());
      if (config == null || !config.enabled()) continue;
      Connector created;
      try {
        created = pack.provider.create(declaration.id());
        if (created == null || !declaration.id().equals(created.id()))
          throw new IllegalStateException("connector id differs from its declaration");
      } catch (RuntimeException e) {
        created = new Unavailable(declaration.id(), e.getClass().getSimpleName());
      }
      var runtime =
          new ConnectorRuntime(
              new Bounded(created, declaration.capabilities()),
              clock,
              new CircuitBreaker(failureThreshold, openFor, clock),
              timeout,
              sleeper);
      runtime.start(config, secrets);
      pack.connectors.put(declaration.id(), new Enabled(declaration, runtime, config));
    }
    return status(pack);
  }

  /** Disables a pack: stops and forgets its connectors. Other packs are untouched. */
  public synchronized PackStatus disable(String packId) {
    Pack pack = packs.get(packId);
    if (pack == null) throw new IllegalArgumentException("Unknown connector pack " + packId);
    stopAll(pack);
    pack.enabled = false;
    return status(pack);
  }

  private static void stopAll(Pack pack) {
    pack.connectors.values().forEach(e -> e.runtime().stop());
    pack.connectors.clear();
  }

  /** The runtime of an enabled, configured connector. */
  public synchronized Optional<ConnectorRuntime> runtime(String connectorId) {
    return packs.values().stream()
        .filter(p -> p.enabled)
        .map(p -> p.connectors.get(connectorId))
        .filter(java.util.Objects::nonNull)
        .map(Enabled::runtime)
        .findFirst();
  }

  public synchronized List<PackStatus> statuses() {
    List<PackStatus> out = new ArrayList<>(packs.values().stream().map(this::status).toList());
    out.addAll(rejected);
    return List.copyOf(out);
  }

  public synchronized Optional<PackStatus> status(String packId) {
    return Optional.ofNullable(packs.get(packId)).map(this::status);
  }

  private PackStatus status(Pack pack) {
    List<ConnectorView> views = new ArrayList<>();
    for (var declaration : pack.manifest.connectors()) {
      Enabled e = pack.connectors.get(declaration.id());
      if (e == null) {
        views.add(
            new ConnectorView(
                declaration.id(),
                declaration.system(),
                ConnectorState.DISABLED.name(),
                pack.enabled ? "not configured" : "pack disabled",
                declaration.capabilities(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of()));
        continue;
      }
      views.add(
          new ConnectorView(
              declaration.id(),
              declaration.system(),
              e.runtime().state().name(),
              e.runtime().reason(),
              declaration.capabilities(),
              e.runtime().state().acceptsOperations()
                  ? Optional.of(e.runtime().capabilities())
                  : Optional.empty(),
              Optional.of(e.runtime().circuit().name()),
              new TreeMap<>(e.config().settings()).keySet().stream().toList(),
              new TreeMap<>(e.config().secrets()).keySet().stream().toList()));
    }
    PackState state;
    String reason;
    if (!pack.enabled) {
      state = PackState.DISABLED;
      reason = "not enabled";
    } else if (pack.connectors.isEmpty()) {
      state = PackState.IDLE;
      reason = "no connector configured";
    } else {
      long working =
          pack.connectors.values().stream()
              .filter(c -> c.runtime().state() == ConnectorState.READY)
              .count();
      long serving =
          pack.connectors.values().stream()
              .filter(c -> c.runtime().state().acceptsOperations())
              .count();
      if (working == pack.connectors.size()) {
        state = PackState.READY;
        reason = "all configured connectors ready";
      } else if (serving == 0) {
        state = PackState.FAILED;
        reason = "no configured connector is working";
      } else {
        state = PackState.DEGRADED;
        reason = (pack.connectors.size() - working) + " connector(s) not ready";
      }
    }
    return new PackStatus(
        pack.manifest.id(),
        pack.manifest.version(),
        pack.manifest.publisher(),
        pack.manifest.systems().stream().sorted().toList(),
        spi(pack.manifest),
        state,
        reason,
        views);
  }

  /** Stops every enabled connector (application shutdown). */
  public synchronized void close() {
    for (Pack pack : packs.values()) {
      stopAll(pack);
      pack.enabled = false;
    }
  }

  /**
   * Holds a connector to its declaration: reported capabilities are intersected with it, and a
   * connector that starts with more than declared fails (and is stopped). A failed start also stops
   * the connector, so partially created clients are released.
   */
  private static final class Bounded implements Connector {
    private final Connector inner;
    private final ConnectorCapabilities declared;

    Bounded(Connector inner, ConnectorCapabilities declared) {
      this.inner = inner;
      this.declared = declared;
    }

    @Override
    public String id() {
      return inner.id();
    }

    @Override
    public ConfigSchema configSchema() {
      return inner.configSchema();
    }

    @Override
    public ConnectorCapabilities capabilities() {
      var actual = inner.capabilities();
      var access = EnumSet.noneOf(ConnectorCapabilities.Access.class);
      actual.access().stream().filter(declared.access()::contains).forEach(access::add);
      var modes = EnumSet.noneOf(io.qwenbridge.core.retrieval.RetrievalMode.class);
      actual.modes().stream().filter(declared.modes()::contains).forEach(modes::add);
      return new ConnectorCapabilities(access, modes, actual.streaming() && declared.streaming());
    }

    @Override
    public void start(ConnectorConfig config, SecretResolver secrets) throws Exception {
      try {
        inner.start(config, secrets);
        if (!inner.capabilities().within(declared)) throw new CapabilityViolation();
      } catch (Exception e) {
        try {
          inner.stop();
        } catch (RuntimeException ignored) {
          // The start failure is what is reported.
        }
        throw e;
      }
    }

    @Override
    public boolean healthy() {
      return inner.healthy();
    }

    @Override
    public void stop() {
      inner.stop();
    }

    @Override
    public Optional<SearchProvider> search() {
      return inner.search();
    }
  }

  /** Stands in for a connector the provider could not create; it fails to start. */
  private record Unavailable(String id, String cause) implements Connector {
    @Override
    public ConfigSchema configSchema() {
      return ConfigSchema.NONE;
    }

    @Override
    public ConnectorCapabilities capabilities() {
      return new ConnectorCapabilities(EnumSet.noneOf(ConnectorCapabilities.Access.class), null);
    }

    @Override
    public void start(ConnectorConfig config, SecretResolver secrets) {
      throw new IllegalStateException("connector could not be created: " + cause);
    }
  }
}

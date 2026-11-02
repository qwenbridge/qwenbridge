package io.qwenbridge.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.pack.*;
import io.qwenbridge.packs.*;
import io.qwenbridge.security.ApiIdentity;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.*;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import java.util.jar.JarInputStream;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Signed, tenant-owned pack lifecycle. Bundled packs are trusted as part of the application
 * artifact.
 */
@ApplicationScoped
public final class ManagedPacks {
  /**
   * A signed pack package (QB-255, QB-275, QB-276). The publisher's key signs the manifest with the
   * JAR digest; optional release metadata is signed separately by the same key and bound to the
   * same digest, so it cannot be moved to another package or changed.
   */
  public record SignedPack(
      String keyId,
      String manifest,
      String jar,
      String signature,
      String metadata,
      String metadataSignature) {
    public SignedPack(String keyId, String manifest, String jar, String signature) {
      this(keyId, manifest, jar, signature, null, null);
    }
  }

  /**
   * Publisher-supplied release information (QB-275), verified with the publisher's signature. The
   * publisher's identity is the signing key id (which the manifest must name), never a name written
   * here.
   */
  public record PackageMetadata(
      String description, String documentation, String contact, String releaseNotes) {
    public PackageMetadata {
      for (String value : new String[] {description, documentation, contact, releaseNotes})
        if (value != null && value.length() > 2000)
          throw new IllegalArgumentException("Package metadata value too long");
      if (documentation != null && !documentation.startsWith("https://"))
        throw new IllegalArgumentException("Package documentation must be an https URL");
    }
  }

  public record Version(
      PackManifest manifest, String digest, SignedPack artifact, boolean bundled) {}

  public record State(
      long revision,
      Map<String, Version> versions,
      Map<String, String> selected,
      Set<String> enabled,
      Map<String, List<String>> history,
      Map<String, Map<String, String>> configuration) {
    public State {
      versions = Map.copyOf(versions);
      selected = Map.copyOf(selected);
      enabled = Set.copyOf(enabled);
      history =
          Map.copyOf(
              history.entrySet().stream()
                  .collect(
                      java.util.stream.Collectors.toUnmodifiableMap(
                          Map.Entry::getKey, e -> List.copyOf(e.getValue()))));
      configuration =
          Map.copyOf(
              configuration.entrySet().stream()
                  .collect(
                      java.util.stream.Collectors.toUnmodifiableMap(
                          Map.Entry::getKey, e -> Map.copyOf(e.getValue()))));
    }
  }

  public record View(
      PackManifest manifest,
      String digest,
      boolean active,
      boolean bundled,
      PackageMetadata metadata) {
    public View(PackManifest manifest, String digest, boolean active, boolean bundled) {
      this(manifest, digest, active, bundled, null);
    }
  }

  /**
   * What a rollback restored and what it cannot undo (QB-258). Pack versions and their
   * configuration are restored; effects a pack had while it ran are not.
   */
  public record Rollback(
      String id, String from, String to, boolean configurationRestored, List<String> notReverted) {
    public Rollback {
      notReverted = List.copyOf(notReverted);
    }
  }

  static final List<String> NOT_REVERTED =
      List.of(
          "data or external state the pack changed while it was active",
          "tenant schema versions registered while the newer version was active",
          "the newer version stays installed; installed versions are immutable");

  private final PlatformStore store;
  private final ObjectMapper mapper;
  private final Entitlements entitlements;
  private final PackSecrets secrets;
  private final Map<String, String> trusted;
  private final Map<String, Loaded> loaded = new HashMap<>();
  private final String runtimeVersion;
  private final Set<String> revokedKeys;
  private final Set<String> revokedDigests;

  private static final class Loaded {
    final String fingerprint;
    final QwenBridgePack pack;
    final URLClassLoader loader;
    final Path file;
    int users;
    boolean retired;

    Loaded(String fingerprint, QwenBridgePack pack, URLClassLoader loader, Path file) {
      this.fingerprint = fingerprint;
      this.pack = pack;
      this.loader = loader;
      this.file = file;
    }

    void close() {
      try {
        pack.close();
      } finally {
        try {
          if (loader != null) loader.close();
          if (file != null) Files.deleteIfExists(file);
        } catch (IOException ex) {
          throw new IllegalStateException("Cannot close pack resources", ex);
        }
      }
    }
  }

  public ManagedPacks(
      PlatformStore store,
      ObjectMapper mapper,
      Entitlements entitlements,
      PackSecrets secrets,
      String keys) {
    this(store, mapper, entitlements, secrets, keys, configuredRuntime());
  }

  private static String configuredRuntime() {
    try {
      return org.eclipse.microprofile.config.ConfigProvider.getConfig()
          .getOptionalValue("qwenbridge.version", String.class)
          .orElse("0.0.0");
    } catch (RuntimeException e) {
      return "0.0.0";
    }
  }

  public ManagedPacks(
      PlatformStore store,
      ObjectMapper mapper,
      Entitlements entitlements,
      PackSecrets secrets,
      String keys,
      String runtimeVersion) {
    this(
        store,
        mapper,
        entitlements,
        secrets,
        keys,
        runtimeVersion,
        Optional.empty(),
        Optional.empty());
  }

  /**
   * @param revokedKeys publisher key ids no longer trusted (QB-276): their packages are refused,
   *     including ones already installed
   * @param revokedDigests package digests (SHA-256 of the JAR) refused wherever they come from
   */
  @jakarta.inject.Inject
  public ManagedPacks(
      PlatformStore store,
      ObjectMapper mapper,
      Entitlements entitlements,
      PackSecrets secrets,
      @ConfigProperty(name = "qwenbridge.packs.trusted-publishers", defaultValue = "{}")
          String keys,
      @ConfigProperty(name = "qwenbridge.version", defaultValue = "0.0.0") String runtimeVersion,
      @ConfigProperty(name = "qwenbridge.packs.revoked-keys") Optional<List<String>> revokedKeys,
      @ConfigProperty(name = "qwenbridge.packs.revoked-digests")
          Optional<List<String>> revokedDigests) {
    this.revokedKeys = Set.copyOf(revokedKeys.orElse(List.of()));
    this.revokedDigests =
        revokedDigests.orElse(List.of()).stream()
            .map(d -> d.toLowerCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    var plain = java.util.regex.Pattern.compile("^(\\d+\\.\\d+\\.\\d+)").matcher(runtimeVersion);
    this.runtimeVersion = plain.find() ? plain.group(1) : "0.0.0";
    this.store = store;
    this.mapper = mapper.copy();
    this.entitlements = entitlements;
    this.secrets = secrets;
    try {
      trusted = Map.copyOf(mapper.readValue(keys, new TypeReference<Map<String, String>>() {}));
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalArgumentException("Invalid publisher trust configuration", ex);
    }
    SignedDocuments.requirePublicKeys(trusted, "qwenbridge.packs.trusted-publishers");
  }

  /**
   * QB-264: the entitlements a pack version needs, decided by the server from facts it controls
   * (bundled with the application or not, and the pack kind), never from manifest metadata. A pack
   * cannot gain or skip a paid capability by what its manifest declares.
   */
  static Set<String> requiredFeatures(Version version) {
    Set<String> required = new TreeSet<>();
    required.add(version.bundled() ? "builtin-packs" : "external-packs");
    if (version.manifest().kind() == PackManifest.Kind.POLICY) required.add("tenant-policy");
    return Set.copyOf(required);
  }

  private void requireEntitled(String tenant, Version version) {
    for (String feature : new TreeSet<>(requiredFeatures(version)))
      entitlements.require(tenant, feature);
  }

  /**
   * QB-272: enabled packs the tenant's current license no longer covers. They stay installed,
   * enabled and configured, but cannot be used until the entitlement returns.
   */
  public List<String> suspended(String tenant) {
    State s = state(tenant);
    var features = entitlements.status(tenant).features();
    return s.enabled().stream()
        .sorted()
        .filter(
            id -> {
              Version v = s.versions().get(id + "@" + s.selected().get(id));
              return v != null && !features.containsAll(requiredFeatures(v));
            })
        .toList();
  }

  private State initial() {
    Map<String, Version> versions = new HashMap<>();
    Map<String, String> selected = new HashMap<>();
    for (QwenBridgePack p : List.of(new CommercePack(), new LibraryPack())) {
      var m = p.manifest();
      versions.put(
          m.id() + "@" + m.version(), new Version(m, "bundled:" + m.version(), null, true));
      selected.put(m.id(), m.version());
      p.close();
    }
    return new State(0, versions, selected, Set.of("commerce", "library"), Map.of(), Map.of());
  }

  public State state(String tenant) {
    State s = store.read(tenant, "packs", "registry", State.class);
    return s == null ? initial() : s;
  }

  public List<View> list(String tenant) {
    State s = state(tenant);
    return s.versions().values().stream()
        .map(
            v ->
                new View(
                    v.manifest(),
                    v.digest(),
                    s.enabled().contains(v.manifest().id())
                        && v.manifest().version().equals(s.selected().get(v.manifest().id())),
                    v.bundled(),
                    v.artifact() == null ? null : metadataOf(v.artifact())))
        .toList();
  }

  /** The verified metadata of a package, or null when it has none. */
  PackageMetadata metadataOf(SignedPack packet) {
    if (packet.metadata() == null) return null;
    try {
      return mapper.readValue(packet.metadata(), PackageMetadata.class);
    } catch (IOException ex) {
      throw new IllegalArgumentException("Invalid package metadata", ex);
    }
  }

  public Version inspect(SignedPack packet) {
    if (packet == null
        || packet.manifest() == null
        || packet.manifest().length() > 65536
        || packet.jar() == null
        || packet.jar().length() > 2800000)
      throw new IllegalArgumentException("Pack artifact exceeds supported bounds");
    byte[] jar;
    try {
      jar = Base64.getDecoder().decode(packet.jar());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Pack JAR is not Base64");
    }
    if (jar.length > 2_097_152) throw new IllegalArgumentException("Pack JAR exceeds 2 MiB");
    // QB-276: revocation is checked first and applies to installed packages too (load re-inspects).
    if (packet.keyId() != null && revokedKeys.contains(packet.keyId()))
      throw new IllegalArgumentException("Publisher key " + packet.keyId() + " is revoked");
    String digest = SignedDocuments.digest(jar);
    if (revokedDigests.contains(digest))
      throw new IllegalArgumentException("Package " + digest + " is revoked");
    SignedDocuments.verify(
        trusted,
        packet.keyId(),
        SignedDocuments.packBytes(packet.manifest(), jar),
        packet.signature());
    if (packet.metadata() != null || packet.metadataSignature() != null) {
      if (packet.metadata() == null
          || packet.metadataSignature() == null
          || packet.metadata().length() > 16384)
        throw new IllegalArgumentException("Package metadata must be signed");
      try {
        SignedDocuments.verify(
            trusted,
            packet.keyId(),
            SignedDocuments.metadataBytes(digest, packet.metadata()),
            packet.metadataSignature());
      } catch (IllegalArgumentException ex) {
        throw new IllegalArgumentException("Invalid package metadata signature", ex);
      }
      metadataOf(packet);
    }
    try {
      PackManifest manifest = mapper.readValue(packet.manifest(), PackManifest.class);
      if (!manifest.publisher().equals(packet.keyId()))
        throw new IllegalArgumentException("Publisher identity mismatch");
      // QB-257: an unsupported runtime is refused from metadata alone, before any pack code runs.
      if (manifest.runtime() != null && !manifest.runtime().accepts(runtimeVersion))
        throw new IllegalArgumentException(
            "Pack requires runtime ["
                + manifest.runtime().minimum()
                + ", "
                + (manifest.runtime().maximumExclusive() == null
                    ? "any"
                    : manifest.runtime().maximumExclusive())
                + "), this runtime is "
                + runtimeVersion);
      long bytes = 0;
      int entries = 0;
      try (var input = new JarInputStream(new ByteArrayInputStream(jar))) {
        var buffer = new byte[8192];
        while (input.getNextJarEntry() != null) {
          if (++entries > 1000) throw new IllegalArgumentException("Too many pack entries");
          for (int n; (n = input.read(buffer)) != -1; )
            if ((bytes += n) > 8_388_608)
              throw new IllegalArgumentException("Expanded pack exceeds 8 MiB");
        }
      }
      return new Version(manifest, digest, packet, false);
    } catch (IOException ex) {
      throw new IllegalArgumentException("Invalid pack artifact", ex);
    }
  }

  public Version inspectExecutable(SignedPack packet) {
    Version version = inspect(packet);
    Loaded candidate = load("validation", version, Map.of(), false);
    candidate.close();
    return version;
  }

  public View install(ApiIdentity.Principal caller, SignedPack packet) {
    caller.require("packs:write");
    entitlements.require(caller.tenant(), "external-packs");
    Version v = inspect(packet);
    Loaded candidate = load(caller.tenant(), v, Map.of(), false);
    candidate.close();
    store.update(
        caller.tenant(),
        "packs",
        "registry",
        State.class,
        null,
        current -> {
          State s = current == null ? initial() : current;
          Map<String, Version> versions = new HashMap<>(s.versions());
          String key = v.manifest().id() + "@" + v.manifest().version();
          Version old = versions.get(key);
          if (old != null
              && (!old.digest().equals(v.digest()) || !old.manifest().equals(v.manifest())))
            throw new IllegalArgumentException("Installed versions are immutable");
          if (versions.size() >= 100 && !versions.containsKey(key))
            throw new IllegalArgumentException("Pack version capacity reached");
          versions.put(key, v);
          Map<String, String> selected = new HashMap<>(s.selected());
          selected.putIfAbsent(v.manifest().id(), v.manifest().version());
          return new State(
              s.revision() + 1, versions, selected, s.enabled(), s.history(), s.configuration());
        });
    return new View(v.manifest(), v.digest(), false, false);
  }

  public void activate(
      ApiIdentity.Principal caller, String id, String version, Map<String, String> config) {
    caller.require("packs:write");
    PackManifest.validId(id);
    PackManifest.versionParts(version);
    State before = state(caller.tenant());
    Version v = before.versions().get(id + "@" + version);
    if (v == null) throw new IllegalArgumentException("Pack version is not installed");
    requireEntitled(caller.tenant(), v);
    validateConfig(v.manifest(), config);
    Map<String, String> selected = new HashMap<>(before.selected());
    selected.put(id, version);
    Set<String> enabled = new HashSet<>(before.enabled());
    enabled.add(id);
    Map<String, List<String>> history = new HashMap<>(before.history());
    String previous = before.selected().get(id);
    if (previous != null && !previous.equals(version)) {
      var h = new ArrayList<>(history.getOrDefault(id, List.of()));
      h.addFirst(previous);
      history.put(id, h.stream().limit(8).toList());
    }
    Map<String, Map<String, String>> configuration = new HashMap<>(before.configuration());
    configuration.put(id + "@" + version, Map.copyOf(config));
    State after =
        new State(
            before.revision() + 1, before.versions(), selected, enabled, history, configuration);
    validateDependencies(after);
    Loaded candidate = load(caller.tenant(), v, config, true);
    try {
      requireUniqueDomain(caller.tenant(), before, id, candidate.pack);
    } catch (RuntimeException ex) {
      candidate.close();
      throw ex;
    }
    try {
      replace(caller.tenant(), before.revision(), after);
    } catch (RuntimeException ex) {
      candidate.close();
      throw ex;
    }
    synchronized (loaded) {
      State latest = state(caller.tenant());
      if (latest.revision() != after.revision()) {
        candidate.close();
        throw new jakarta.ws.rs.ClientErrorException(
            "Pack state changed during activation; inspect the current version", 409);
      }
      retire(loaded.put(caller.tenant() + ":" + id, candidate));
    }
  }

  public void disable(ApiIdentity.Principal caller, String id) {
    caller.require("packs:write");
    State before = state(caller.tenant());
    var enabled = new HashSet<>(before.enabled());
    enabled.remove(id);
    State after =
        new State(
            before.revision() + 1,
            before.versions(),
            before.selected(),
            enabled,
            before.history(),
            before.configuration());
    validateDependencies(after);
    replace(caller.tenant(), before.revision(), after);
    synchronized (loaded) {
      retire(loaded.remove(caller.tenant() + ":" + id));
    }
  }

  /**
   * QB-257: two enabled Domain Packs cannot provide the same domain. The candidate's domain is
   * compared with every other enabled Domain Pack of the tenant before the state changes.
   */
  private void requireUniqueDomain(String tenant, State before, String id, QwenBridgePack pack) {
    if (!(pack instanceof DomainPack domain)) return;
    String wanted = domain.definition().id();
    for (String other : before.enabled()) {
      if (other.equals(id)) continue;
      Version v = before.versions().get(other + "@" + before.selected().get(other));
      if (v == null || v.manifest().kind() != PackManifest.Kind.DOMAIN) continue;
      String provided = withPack(tenant, other, p -> ((DomainPack) p).definition().id());
      if (wanted.equals(provided))
        throw new IllegalArgumentException(
            "Conflicting capability: domain " + wanted + " is already provided by pack " + other);
    }
  }

  /**
   * Restores the previously active version with its recorded configuration (QB-258). The restored
   * version goes through the same validation as any activation (configuration, dependencies,
   * entitlements, domain conflicts); when it no longer passes, the rollback is refused with the
   * reason and nothing changes.
   */
  public Rollback rollback(ApiIdentity.Principal caller, String id) {
    State s = state(caller.tenant());
    List<String> h = s.history().getOrDefault(id, List.of());
    if (h.isEmpty())
      throw new IllegalArgumentException(
          "Rollback of " + id + " is not supported: no previous pack version");
    String from = s.selected().get(id);
    String to = h.getFirst();
    try {
      activate(caller, id, to, s.configuration().getOrDefault(id + "@" + to, Map.of()));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(
          "Rollback of " + id + " to " + to + " is not supported: " + ex.getMessage(), ex);
    }
    return new Rollback(id, from, to, true, NOT_REVERTED);
  }

  private void replace(String tenant, long revision, State after) {
    store.update(
        tenant,
        "packs",
        "registry",
        State.class,
        null,
        current -> {
          State s = current == null ? initial() : current;
          if (s.revision() != revision)
            throw new jakarta.ws.rs.ClientErrorException("Pack state changed; retry", 409);
          return after;
        });
  }

  private void validateDependencies(State state) {
    for (String id : state.enabled()) {
      Version v = state.versions().get(id + "@" + state.selected().get(id));
      for (var dependency : v.manifest().dependencies())
        if (!state.enabled().contains(dependency.id())
            || !dependency.accepts(state.selected().get(dependency.id())))
          throw new IllegalArgumentException(
              "Unsatisfied dependency: " + id + " requires " + dependency.id());
      visit(state, id, new HashSet<>(), new HashSet<>());
    }
  }

  private void visit(State s, String id, Set<String> visiting, Set<String> done) {
    if (done.contains(id)) return;
    if (!visiting.add(id)) throw new IllegalArgumentException("Cyclic pack dependency");
    for (var d : s.versions().get(id + "@" + s.selected().get(id)).manifest().dependencies())
      visit(s, d.id(), visiting, done);
    visiting.remove(id);
    done.add(id);
  }

  private void validateConfig(PackManifest m, Map<String, String> config) {
    if (!m.configuration().keySet().containsAll(config.keySet()))
      throw new IllegalArgumentException("Unknown pack configuration");
    m.configuration()
        .forEach(
            (key, field) -> {
              String value = config.get(key);
              if (value == null) {
                if (field.required())
                  throw new IllegalArgumentException("Missing pack configuration: " + key);
                return;
              }
              switch (field.type()) {
                case INTEGER -> Integer.parseInt(value);
                case BOOLEAN -> {
                  if (!Set.of("true", "false").contains(value))
                    throw new IllegalArgumentException("Invalid boolean configuration");
                }
                case URI -> {
                  var uri = java.net.URI.create(value);
                  if (uri.getScheme() == null)
                    throw new IllegalArgumentException("Absolute URI required");
                }
                case SECRET_REF -> {
                  char[] resolved = secrets.resolve(value);
                  Arrays.fill(resolved, '\0');
                }
                default -> {
                  if (value.length() > 4096)
                    throw new IllegalArgumentException("Configuration value too long");
                }
              }
            });
  }

  private Loaded load(String tenant, Version version, Map<String, String> config, boolean start) {
    URLClassLoader loader = null;
    Path file = null;
    QwenBridgePack pack = null;
    try {
      if (version.bundled())
        pack =
            switch (version.manifest().id()) {
              case "commerce" -> new CommercePack();
              case "library" -> new LibraryPack();
              default -> throw new IllegalArgumentException("Unknown bundled pack");
            };
      else {
        inspect(version.artifact());
        file = Files.createTempFile("qwenbridge-pack-", ".jar");
        Files.write(file, Base64.getDecoder().decode(version.artifact().jar()));
        loader =
            new URLClassLoader(
                new java.net.URL[] {file.toUri().toURL()}, QwenBridgePack.class.getClassLoader());
        final ClassLoader child = loader;
        var providers =
            ServiceLoader.load(QwenBridgePack.class, loader).stream()
                .filter(p -> p.type().getClassLoader() == child)
                .toList();
        if (providers.size() != 1)
          throw new IllegalArgumentException("A pack JAR must contain exactly one pack provider");
        pack = providers.getFirst().get();
      }
      if (!pack.manifest().equals(version.manifest()))
        throw new IllegalArgumentException("Executable manifest differs from signed manifest");
      if (version.manifest().kind() == PackManifest.Kind.DOMAIN && !(pack instanceof DomainPack)
          || version.manifest().kind() == PackManifest.Kind.CONNECTOR
              && !(pack instanceof ConnectorPack)
          || version.manifest().kind() == PackManifest.Kind.POLICY && !(pack instanceof PolicyPack))
        throw new IllegalArgumentException("Pack kind and implementation differ");
      if (pack instanceof PolicyPack policy) validatePolicy(policy);
      if (start) pack.start(new QwenBridgePack.Context(tenant, config, secrets));
      return new Loaded(version.digest() + new java.util.TreeMap<>(config), pack, loader, file);
    } catch (IOException | RuntimeException | ServiceConfigurationError ex) {
      if (pack != null)
        try {
          pack.close();
        } catch (RuntimeException ignored) {
        }
      try {
        if (loader != null) loader.close();
        if (file != null) Files.deleteIfExists(file);
      } catch (IOException cleanup) {
        ex.addSuppressed(cleanup);
      }
      throw new IllegalArgumentException("Pack cannot be loaded", ex);
    }
  }

  /**
   * QB-249, QB-251, QB-252: a policy pack must use the supported evaluation contract, name only
   * operations the central catalog knows, and declare its domains and order within bounds. Anything
   * else is refused before the pack is activated; nothing about it is guessed.
   */
  static void validatePolicy(PolicyPack policy) {
    if (policy.contract() != PolicyPack.CONTRACT)
      throw new IllegalArgumentException(
          "Unsupported policy contract "
              + policy.contract()
              + " (runtime: "
              + PolicyPack.CONTRACT
              + ")");
    Set<String> operations = policy.operations();
    if (operations == null || operations.isEmpty())
      throw new IllegalArgumentException("A policy pack must name its operations");
    for (String op : operations)
      if (!op.equals("*") && io.qwenbridge.security.Authorization.CATALOG.find(op).isEmpty())
        throw new IllegalArgumentException("Policy pack names an unsupported operation: " + op);
    Set<String> domains = policy.domains();
    if (domains == null || domains.isEmpty())
      throw new IllegalArgumentException("A policy pack must name its domains");
    for (String domain : domains) if (!domain.equals("*")) PackManifest.validId(domain);
    if (policy.order() < -10_000 || policy.order() > 10_000)
      throw new IllegalArgumentException("Policy order must be within -10000..10000");
  }

  public <T> T withPack(String tenant, String id, Function<QwenBridgePack, T> action) {
    Loaded current;
    synchronized (loaded) {
      String key = tenant + ":" + id;
      State state = state(tenant);
      if (!state.enabled().contains(id)) {
        retire(loaded.remove(key));
        throw new jakarta.ws.rs.ClientErrorException("Pack is disabled or unavailable: " + id, 409);
      }
      Version version = state.versions().get(id + "@" + state.selected().get(id));
      try {
        requireEntitled(tenant, version);
      } catch (RuntimeException ex) {
        retire(loaded.remove(key));
        throw ex;
      }
      Map<String, String> config =
          state.configuration().getOrDefault(id + "@" + state.selected().get(id), Map.of());
      current = loaded.get(key);
      if (current == null
          || !current.fingerprint.equals(version.digest() + new java.util.TreeMap<>(config))) {
        if (loaded.size() >= 1000 && !loaded.containsKey(key))
          throw new jakarta.ws.rs.ClientErrorException("Pack runtime capacity reached", 429);
        current = load(tenant, version, config, true);
        retire(loaded.put(key, current));
      }
      current.users++;
    }
    try {
      return action.apply(current.pack);
    } finally {
      synchronized (loaded) {
        current.users--;
        if (current.retired && current.users == 0) current.close();
      }
    }
  }

  private void retire(Loaded value) {
    if (value != null) {
      value.retired = true;
      if (value.users == 0) value.close();
    }
  }

  @PreDestroy
  public void close() {
    synchronized (loaded) {
      RuntimeException failure = null;
      for (var pack : loaded.values())
        try {
          retire(pack);
        } catch (RuntimeException ex) {
          if (failure == null) failure = ex;
          else failure.addSuppressed(ex);
        }
      loaded.clear();
      if (failure != null) throw failure;
    }
  }
}

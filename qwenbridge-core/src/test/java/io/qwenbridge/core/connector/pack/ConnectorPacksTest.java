package io.qwenbridge.core.connector.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.core.connector.ConfigSchema;
import io.qwenbridge.core.connector.Connector;
import io.qwenbridge.core.connector.ConnectorCapabilities;
import io.qwenbridge.core.connector.ConnectorCapabilities.Access;
import io.qwenbridge.core.connector.ConnectorConfig;
import io.qwenbridge.core.connector.ConnectorResult;
import io.qwenbridge.core.connector.ConnectorState;
import io.qwenbridge.core.connector.EnvironmentSecrets;
import io.qwenbridge.core.connector.RetryPolicy;
import io.qwenbridge.core.connector.SecretRef;
import io.qwenbridge.core.connector.SecretResolver;
import io.qwenbridge.core.retrieval.RetrievalMode;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * QB-237 – QB-242: Connector Packs package Core {@link Connector}s; disabled packs create nothing;
 * capabilities are inspectable data bounded by the declaration; configuration is validated without
 * exposing values; health is per connector and per pack; failures stay inside their pack.
 */
class ConnectorPacksTest {

  static final ConnectorCapabilities READ_KEYWORD =
      new ConnectorCapabilities(EnumSet.of(Access.READ), EnumSet.of(RetrievalMode.KEYWORD));
  static final ConfigSchema SCHEMA =
      new ConfigSchema(Set.of("url"), Set.of("access"), Set.of(), Set.of("password"));
  static final SecretResolver SECRETS =
      new EnvironmentSecrets(Map.of("DEMO_PASSWORD", "s3cret-value")::get, Optional.empty());

  /** A connector whose behaviour each test chooses. */
  static final class Fake implements Connector {
    final String id;
    volatile boolean started;
    volatile boolean stopped;
    volatile boolean writable;
    volatile boolean rejectCredentials;
    volatile String passwordSeen = "";

    Fake(String id) {
      this.id = id;
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public ConfigSchema configSchema() {
      return SCHEMA;
    }

    @Override
    public ConnectorCapabilities capabilities() {
      return new ConnectorCapabilities(
          writable ? EnumSet.allOf(Access.class) : EnumSet.of(Access.READ),
          EnumSet.of(RetrievalMode.KEYWORD));
    }

    @Override
    public void start(ConnectorConfig config, SecretResolver secrets) {
      writable = "read-write".equals(config.settings().get("access"));
      var password = config.secrets().get("password");
      if (password != null) passwordSeen = secrets.resolve(password).revealString();
      if (rejectCredentials) throw new SecurityException("401 for user admin:" + passwordSeen);
      started = true;
    }

    @Override
    public void stop() {
      stopped = true;
    }
  }

  /** A provider that records what it creates. */
  static final class Provider implements ConnectorPackProvider {
    final ConnectorPackManifest manifest;
    final Map<String, Fake> created = new java.util.concurrent.ConcurrentHashMap<>();
    final AtomicInteger creations = new AtomicInteger();
    volatile boolean rejectCredentials;

    Provider(
        String id, String spiMin, String spiMax, ConnectorCapabilities caps, String... connectors) {
      manifest =
          new ConnectorPackManifest(
              id,
              "1.0.0",
              "acme",
              spiMin,
              spiMax,
              java.util.Arrays.stream(connectors)
                  .map(c -> new ConnectorPackManifest.Declaration(c, c + "-db", "demo", caps))
                  .toList());
    }

    Provider(String id, String... connectors) {
      this(id, "1.0.0", "2.0.0", READ_KEYWORD, connectors);
    }

    @Override
    public ConnectorPackManifest manifest() {
      return manifest;
    }

    @Override
    public Connector create(String connectorId) {
      creations.incrementAndGet();
      var fake = new Fake(connectorId);
      fake.rejectCredentials = rejectCredentials;
      created.put(connectorId, fake);
      return fake;
    }
  }

  static ConnectorConfig config(
      String id, Map<String, String> settings, Map<String, SecretRef> secrets) {
    return new ConnectorConfig(ConnectorConfig.VERSION, id, true, settings, secrets);
  }

  static ConnectorConfig valid(String id) {
    return config(
        id, Map.of("url", "demo://" + id), Map.of("password", new SecretRef("env:DEMO_PASSWORD")));
  }

  static ConnectorPacks host(Duration timeout) {
    return new ConnectorPacks(Clock.systemUTC(), 2, Duration.ofMinutes(5), timeout, d -> {});
  }

  static Map<String, ConnectorPacks.ConnectorView> byId(ConnectorPacks.PackStatus status) {
    var out = new HashMap<String, ConnectorPacks.ConnectorView>();
    status.connectors().forEach(c -> out.put(c.id(), c));
    return out;
  }

  @Test
  void packsAreDiscoveredAsDataAndDisabledPacksCreateNothing() {
    var alpha = new Provider("alpha", "alpha-main", "alpha-replica");
    var beta = new Provider("beta", "beta-main");
    var packs = host(Duration.ofSeconds(2));
    packs.discover(List.of(alpha, beta));
    // QB-238: capabilities and systems are visible without creating or running anything.
    var status = packs.status("alpha").orElseThrow();
    assertEquals(ConnectorPacks.PackState.DISABLED, status.state());
    assertEquals(List.of("alpha-main-db", "alpha-replica-db"), status.systems());
    assertEquals("[1.0.0, 2.0.0)", status.spi());
    for (var c : status.connectors()) {
      assertEquals("DISABLED", c.state());
      assertEquals("pack disabled", c.reason());
      assertEquals(READ_KEYWORD, c.declared());
      assertTrue(c.effective().isEmpty());
    }
    assertEquals(0, alpha.creations.get() + beta.creations.get());
    assertTrue(packs.runtime("alpha-main").isEmpty());
    // QB-241: an enabled pack creates only its configured connectors; the rest stay DISABLED.
    var enabled = packs.enable("alpha", Map.of("alpha-main", valid("alpha-main")), SECRETS);
    assertEquals(ConnectorPacks.PackState.READY, enabled.state());
    assertEquals(Set.of("alpha-main"), alpha.created.keySet());
    assertEquals("not configured", byId(enabled).get("alpha-replica").reason());
    assertEquals(READ_KEYWORD, byId(enabled).get("alpha-main").effective().orElseThrow());
    assertEquals(0, beta.creations.get());
    assertEquals(ConnectorPacks.PackState.IDLE, packs.enable("beta", Map.of(), SECRETS).state());
    assertEquals(0, beta.creations.get());
  }

  @Test
  void incompatibleInvalidAndDuplicatePacksAreRejected() {
    var future = new Provider("future", "2.0.0", "3.0.0", READ_KEYWORD, "future-main");
    var first = new Provider("dup", "dup-one");
    var second = new Provider("dup", "dup-two");
    var clashing = new Provider("clash", "shared");
    var owner = new Provider("owner", "shared");
    ConnectorPackProvider broken =
        new ConnectorPackProvider() {
          @Override
          public ConnectorPackManifest manifest() {
            return new ConnectorPackManifest(
                "Bad Id", "1.0.0", "acme", "1.0.0", "2.0.0", List.of());
          }

          @Override
          public Connector create(String connectorId) {
            throw new AssertionError("never created");
          }
        };
    var packs = host(Duration.ofSeconds(2));
    packs.discover(
        List.of(future, first, second, clashing, owner, broken, new Provider("fine", "fine-main")));
    var states = new TreeMap<String, String>();
    packs
        .statuses()
        .forEach(s -> states.merge(s.id(), s.state() + ":" + s.reason(), (a, b) -> a + "|" + b));
    assertTrue(states.get("future").startsWith("REJECTED:requires Connector SPI [2.0.0, 3.0.0)"));
    assertEquals("REJECTED:duplicate pack id|REJECTED:duplicate pack id", states.get("dup"));
    assertEquals("REJECTED:connector ids claimed by another pack: [shared]", states.get("clash"));
    assertEquals("REJECTED:connector ids claimed by another pack: [shared]", states.get("owner"));
    assertEquals("REJECTED:invalid manifest", states.get(broken.getClass().getName()));
    assertTrue(states.get("fine").startsWith("DISABLED"));
    assertThrows(IllegalArgumentException.class, () -> packs.enable("future", Map.of(), SECRETS));
    var unknown =
        assertThrows(
            IllegalArgumentException.class,
            () -> packs.enable("fine", Map.of("not-in-pack", valid("not-in-pack")), SECRETS));
    assertTrue(unknown.getMessage().contains("has no connectors [not-in-pack]"));
    assertEquals(0, first.creations.get() + second.creations.get() + future.creations.get());
  }

  @Test
  void configurationIsValidatedWithoutExposingValues() {
    var provider = new Provider("demo", "demo-main", "demo-plain", "demo-missing");
    var packs = host(Duration.ofSeconds(2));
    packs.discover(List.of(provider));
    var status =
        packs.enable(
            "demo",
            Map.of(
                "demo-main",
                valid("demo-main"),
                // A secret given as a plain setting is refused before the connector starts.
                "demo-plain",
                config("demo-plain", Map.of("url", "demo://x", "password", "hunter2"), Map.of()),
                "demo-missing",
                config("demo-missing", Map.of(), Map.of())),
            SECRETS);
    var views = byId(status);
    assertEquals("READY", views.get("demo-main").state());
    assertEquals("s3cret-value", provider.created.get("demo-main").passwordSeen);
    assertEquals(List.of("url"), views.get("demo-main").settings());
    assertEquals(List.of("password"), views.get("demo-main").secrets());
    assertEquals("FAILED", views.get("demo-plain").state());
    assertTrue(views.get("demo-plain").reason().contains("secret password must be a reference"));
    assertTrue(views.get("demo-missing").reason().contains("missing setting url"));
    assertFalse(provider.created.get("demo-plain").started);
    assertEquals(ConnectorPacks.PackState.DEGRADED, status.state());
    String shown = packs.statuses().toString();
    for (String value : List.of("s3cret-value", "hunter2", "demo://"))
      assertFalse(shown.contains(value), value);
  }

  @Test
  void capabilitiesNeverExceedTheDeclarationAndMutationIsOptIn() {
    var readOnlyPack = new Provider("ro", "ro-main");
    var writablePack =
        new Provider(
            "rw",
            "1.0.0",
            "2.0.0",
            new ConnectorCapabilities(
                EnumSet.allOf(Access.class), EnumSet.of(RetrievalMode.KEYWORD)),
            "rw-main");
    var packs = host(Duration.ofSeconds(2));
    packs.discover(List.of(readOnlyPack, writablePack));
    // A connector configured to write in a pack that declared read-only fails to start.
    var ro =
        packs.enable(
            "ro",
            Map.of(
                "ro-main",
                config("ro-main", Map.of("url", "demo://a", "access", "read-write"), Map.of())),
            SECRETS);
    assertEquals("FAILED", ro.connectors().getFirst().state());
    assertEquals("start failed: CapabilityViolation", ro.connectors().getFirst().reason());
    assertTrue(readOnlyPack.created.get("ro-main").stopped);
    // A pack that declares writes still only writes when its configuration opts in.
    var rw =
        packs.enable(
            "rw",
            Map.of("rw-main", config("rw-main", Map.of("url", "demo://b"), Map.of())),
            SECRETS);
    assertTrue(rw.connectors().getFirst().effective().orElseThrow().readOnly());
    var write =
        packs.runtime("rw-main").orElseThrow().execute(Access.WRITE, RetryPolicy.NONE, () -> "w");
    assertEquals(
        new ConnectorResult.Failed<>(ConnectorResult.Failure.ACCESS_DENIED, "rw-main", 0), write);
    packs.enable(
        "rw",
        Map.of(
            "rw-main",
            config("rw-main", Map.of("url", "demo://b", "access", "read-write"), Map.of())),
        SECRETS);
    assertTrue(packs.runtime("rw-main").orElseThrow().capabilities().allows(Access.WRITE));
    assertTrue(Access.WRITE.mutating() && Access.DELETE.mutating());
    assertFalse(Access.READ.mutating());
  }

  @Test
  void failuresStayInsideTheirPack() {
    var auth = new Provider("auth", "auth-main");
    auth.rejectCredentials = true;
    var flaky = new Provider("flaky", "flaky-main");
    var slow = new Provider("slow", "slow-main");
    var healthy = new Provider("healthy", "healthy-main");
    var packs = host(Duration.ofMillis(200));
    packs.discover(List.of(auth, flaky, slow, healthy));
    for (String id : List.of("auth", "flaky", "slow", "healthy"))
      packs.enable(id, Map.of(id + "-main", valid(id + "-main")), SECRETS);

    // Authentication error at start: FAILED; the message (with user and password) is not kept.
    var authStatus = packs.status("auth").orElseThrow();
    assertEquals(ConnectorPacks.PackState.FAILED, authStatus.state());
    assertEquals("start failed: SecurityException", authStatus.connectors().getFirst().reason());
    assertTrue(auth.created.get("auth-main").stopped);
    assertFalse(authStatus.toString().contains("s3cret-value"));
    assertFalse(authStatus.toString().contains("admin"));

    // Network outage: calls fail, then the circuit opens and later calls are not attempted.
    var flakyRuntime = packs.runtime("flaky-main").orElseThrow();
    var attempts = new AtomicInteger();
    for (int i = 0; i < 4; i++)
      flakyRuntime.execute(
          Access.READ,
          RetryPolicy.NONE,
          () -> {
            attempts.incrementAndGet();
            throw new IOException("connection refused");
          });
    assertEquals(2, attempts.get());
    assertEquals(
        new ConnectorResult.Failed<>(ConnectorResult.Failure.CIRCUIT_OPEN, "flaky-main", 0),
        flakyRuntime.execute(Access.READ, RetryPolicy.NONE, () -> "x"));
    assertEquals(ConnectorState.DEGRADED, flakyRuntime.state());

    // Timeouts are bounded by the connector's own timeout.
    var slowRuntime = packs.runtime("slow-main").orElseThrow();
    long started = System.nanoTime();
    var timedOut =
        slowRuntime.execute(
            Access.READ,
            RetryPolicy.NONE,
            () -> {
              Thread.sleep(5_000);
              return "late";
            });
    var failed = assertInstanceOf(ConnectorResult.Failed.class, timedOut);
    assertEquals(ConnectorResult.Failure.TIMEOUT, failed.failure());
    assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(2)) < 0);

    // Shutdown of one pack: its connectors stop and refuse work; the others are unchanged.
    assertEquals(ConnectorPacks.PackState.DISABLED, packs.disable("slow").state());
    assertTrue(slow.created.get("slow-main").stopped);
    assertEquals(
        new ConnectorResult.Failed<>(ConnectorResult.Failure.NOT_READY, "slow-main", 0),
        slowRuntime.execute(Access.READ, RetryPolicy.NONE, () -> "x"));
    assertTrue(packs.runtime("slow-main").isEmpty());

    var healthyRuntime = packs.runtime("healthy-main").orElseThrow();
    assertEquals(
        new ConnectorResult.Success<>("ok", 1),
        healthyRuntime.execute(Access.READ, RetryPolicy.NONE, () -> "ok"));
    assertEquals(ConnectorPacks.PackState.READY, packs.status("healthy").orElseThrow().state());
    assertEquals(ConnectorPacks.PackState.DEGRADED, packs.status("flaky").orElseThrow().state());

    packs.close();
    assertTrue(healthy.created.get("healthy-main").stopped);
    for (var s : packs.statuses()) assertEquals(ConnectorPacks.PackState.DISABLED, s.state());
  }
}

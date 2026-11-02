package io.qwenbridge.core.connector;

import static org.junit.jupiter.api.Assertions.*;

import io.qwenbridge.core.connector.ConnectorCapabilities.Access;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** QB-153 – QB-159: connector SPI, configuration, secrets, lifecycle, resilience, isolation. */
class ConnectorTest {

  static final class MutableClock extends Clock {
    Instant now = Instant.parse("2026-11-01T12:00:00Z");

    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return now;
    }
  }

  static final ConfigSchema SCHEMA =
      new ConfigSchema(Set.of("url"), Set.of("index"), Set.of("password"), Set.of("token"));

  static Connector connector(String id, Set<Access> access, boolean failStart) {
    return new Connector() {
      public String id() {
        return id;
      }

      public ConfigSchema configSchema() {
        return SCHEMA;
      }

      public ConnectorCapabilities capabilities() {
        return new ConnectorCapabilities(access, Set.of());
      }

      public void start(ConnectorConfig config, SecretResolver secrets) {
        if (failStart)
          throw new IllegalStateException("cannot reach jdbc:postgresql://user:pw@host");
        secrets.resolve(config.secrets().get("password"));
      }
    };
  }

  static ConnectorConfig valid(String id) {
    return new ConnectorConfig(
        ConnectorConfig.VERSION,
        id,
        true,
        Map.of("url", "https://x"),
        Map.of("password", new SecretRef("env:PG_PASSWORD")));
  }

  static final SecretResolver SECRETS =
      new EnvironmentSecrets(
          name -> name.equals("PG_PASSWORD") ? "s3cret" : null, Optional.empty());

  static ConnectorRuntime runtime(Connector c, MutableClock clock, List<Duration> sleeps) {
    return new ConnectorRuntime(
        c,
        clock,
        new CircuitBreaker(3, Duration.ofSeconds(30), clock),
        Duration.ofMillis(300),
        sleeps::add);
  }

  @Test
  void configurationIsVersionedValidatedAndNeverShowsSecrets() {
    assertTrue(valid("pg").problems(SCHEMA).isEmpty());
    assertTrue(
        ConnectorConfig.disabled("pg").problems(SCHEMA).isEmpty(), "disabled needs no settings");
    var plaintext =
        new ConnectorConfig(
            ConnectorConfig.VERSION,
            "pg",
            true,
            Map.of("url", "u", "password", "hunter2"),
            Map.of());
    var problems = plaintext.problems(SCHEMA);
    assertTrue(
        problems.contains("secret password must be a reference (env: or file:), not a value"));
    assertTrue(problems.contains("missing secret password"));
    assertTrue(problems.stream().noneMatch(p -> p.contains("hunter2")));
    assertFalse(plaintext.toString().contains("hunter2"));
    assertTrue(
        new ConnectorConfig("connector-config/9", "pg", false, Map.of(), Map.of())
            .problems(SCHEMA)
            .getFirst()
            .contains("version"));
    var unknown =
        new ConnectorConfig(
            ConnectorConfig.VERSION,
            "pg",
            true,
            Map.of("url", "u", "colour", "x"),
            Map.of("password", new SecretRef("env:A"), "other", new SecretRef("env:B")));
    assertEquals(
        List.of("unknown setting colour", "unknown secret other"), unknown.problems(SCHEMA));
    assertEquals(
        List.of("missing setting url", "missing secret password"),
        new ConnectorConfig(ConnectorConfig.VERSION, "pg", true, Map.of(), Map.of())
            .problems(SCHEMA));
  }

  @Test
  void secretsAreReferencesResolvedOnlyWhereAllowed(@TempDir Path dir) throws IOException {
    assertThrows(IllegalArgumentException.class, () -> new SecretRef("hunter2"));
    assertThrows(IllegalArgumentException.class, () -> new SecretRef("env:lower"));
    assertThrows(IllegalArgumentException.class, () -> new SecretRef("file:/run/../etc/passwd"));
    Secret s = SECRETS.resolve(new SecretRef("env:PG_PASSWORD"));
    assertEquals("s3cret", s.revealString());
    assertEquals("Secret[****]", s.toString());
    var missing =
        assertThrows(
            IllegalArgumentException.class, () -> SECRETS.resolve(new SecretRef("env:NOPE")));
    assertTrue(missing.getMessage().contains("env:NOPE"));
    Path file = Files.writeString(dir.resolve("db-password"), "fromfile\n");
    var files = new EnvironmentSecrets(n -> null, Optional.of(dir));
    assertEquals("fromfile", files.resolve(new SecretRef("file:" + file)).revealString());
    assertThrows(
        IllegalArgumentException.class,
        () -> files.resolve(new SecretRef("file:/etc/hosts")),
        "outside the root");
    assertThrows(
        IllegalArgumentException.class,
        () -> SECRETS.resolve(new SecretRef("file:" + file)),
        "file secrets off");
  }

  @Test
  void lifecycleFollowsLegalTransitionsAndRecordsWhy() {
    var clock = new MutableClock();
    var ok = runtime(connector("es", EnumSet.allOf(Access.class), false), clock, new ArrayList<>());
    assertEquals(ConnectorState.CREATED, ok.state());
    assertEquals(ConnectorState.READY, ok.start(valid("es"), SECRETS));
    ok.stop();
    assertEquals(ConnectorState.STOPPED, ok.state());
    assertEquals(
        List.of(
            ConnectorState.STARTING,
            ConnectorState.READY,
            ConnectorState.STOPPING,
            ConnectorState.STOPPED),
        ok.history().stream().map(ConnectorRuntime.Transition::to).toList());
    assertEquals(ConnectorState.READY, ok.start(valid("es"), SECRETS), "restart");

    var bad = runtime(connector("pg", EnumSet.allOf(Access.class), true), clock, new ArrayList<>());
    assertEquals(ConnectorState.FAILED, bad.start(valid("pg"), SECRETS));
    assertEquals("start failed: IllegalStateException", bad.reason());
    assertFalse(bad.reason().contains("pw@"), "connection strings never reach the reason");
    var invalid =
        runtime(connector("x", EnumSet.allOf(Access.class), false), clock, new ArrayList<>());
    assertEquals(
        ConnectorState.FAILED,
        invalid.start(
            new ConnectorConfig(ConnectorConfig.VERSION, "x", true, Map.of(), Map.of()), SECRETS));
    assertTrue(invalid.reason().startsWith("invalid configuration"));
    var off =
        runtime(connector("mongo", EnumSet.allOf(Access.class), false), clock, new ArrayList<>());
    assertEquals(ConnectorState.DISABLED, off.start(ConnectorConfig.disabled("mongo"), SECRETS));
    assertFalse(ConnectorState.READY.canMoveTo(ConnectorState.CREATED));
    assertFalse(ConnectorState.DISABLED.acceptsOperations());
  }

  @Test
  void operationsAreCheckedForStateAndAccess() {
    var clock = new MutableClock();
    var readOnly =
        runtime(connector("pg", EnumSet.of(Access.READ), false), clock, new ArrayList<>());
    assertTrue(readOnly.capabilities().readOnly());
    var notStarted = readOnly.execute(Access.READ, RetryPolicy.NONE, () -> 1);
    assertEquals(
        ConnectorResult.Failure.NOT_READY,
        ((ConnectorResult.Failed<Integer>) notStarted).failure());
    readOnly.start(valid("pg"), SECRETS);
    assertInstanceOf(
        ConnectorResult.Success.class, readOnly.execute(Access.READ, RetryPolicy.NONE, () -> 1));
    var calls = new AtomicInteger();
    var write = readOnly.execute(Access.WRITE, RetryPolicy.NONE, calls::incrementAndGet);
    assertEquals(
        ConnectorResult.Failure.ACCESS_DENIED, ((ConnectorResult.Failed<Integer>) write).failure());
    assertEquals(0, calls.get(), "a refused write is never attempted");
  }

  @Test
  void retriesAreBoundedAndOnlyForRetryableFailures() {
    var clock = new MutableClock();
    var sleeps = new ArrayList<Duration>();
    var rt = runtime(connector("es", EnumSet.allOf(Access.class), false), clock, sleeps);
    rt.start(valid("es"), SECRETS);
    var retry = new RetryPolicy(3, Duration.ofMillis(10), t -> t instanceof IOException);
    var attempts = new AtomicInteger();
    var recovered =
        rt.execute(
            Access.READ,
            retry,
            () -> {
              if (attempts.incrementAndGet() < 3) throw new IOException("flaky");
              return "ok";
            });
    assertEquals(new ConnectorResult.Success<>("ok", 3), recovered);
    assertEquals(List.of(Duration.ofMillis(10), Duration.ofMillis(20)), sleeps);
    attempts.set(0);
    var fatal =
        rt.execute(
            Access.READ,
            retry,
            () -> {
              attempts.incrementAndGet();
              throw new IllegalArgumentException("bad query");
            });
    assertEquals(ConnectorResult.Failure.FAILED, ((ConnectorResult.Failed<?>) fatal).failure());
    assertEquals(1, attempts.get(), "a non-retryable failure is not retried");
    var slow =
        rt.execute(
            Access.READ,
            RetryPolicy.NONE,
            () -> {
              Thread.sleep(5_000);
              return "late";
            });
    assertEquals(ConnectorResult.Failure.TIMEOUT, ((ConnectorResult.Failed<?>) slow).failure());
    assertThrows(
        IllegalArgumentException.class, () -> new RetryPolicy(10, Duration.ZERO, t -> true));
  }

  @Test
  void theCircuitOpensFailsFastAndRecoversThroughAHalfOpenTrial() {
    var clock = new MutableClock();
    var rt = runtime(connector("os", EnumSet.allOf(Access.class), false), clock, new ArrayList<>());
    rt.start(valid("os"), SECRETS);
    var calls = new AtomicInteger();
    for (int i = 0; i < 3; i++)
      rt.execute(
          Access.READ,
          RetryPolicy.NONE,
          () -> {
            calls.incrementAndGet();
            throw new IOException("down");
          });
    assertEquals(CircuitBreaker.State.OPEN, rt.circuit());
    assertEquals(ConnectorState.DEGRADED, rt.state());
    var refused = rt.execute(Access.READ, RetryPolicy.NONE, () -> calls.incrementAndGet());
    assertEquals(
        ConnectorResult.Failure.CIRCUIT_OPEN, ((ConnectorResult.Failed<?>) refused).failure());
    assertEquals(3, calls.get(), "an open circuit does not call the connector");
    clock.now = clock.now.plusSeconds(31);
    assertEquals(CircuitBreaker.State.HALF_OPEN, rt.circuit());
    assertInstanceOf(
        ConnectorResult.Success.class, rt.execute(Access.READ, RetryPolicy.NONE, () -> "ok"));
    assertEquals(CircuitBreaker.State.CLOSED, rt.circuit());
    assertEquals(ConnectorState.READY, rt.state());
    var breaker = new CircuitBreaker(1, Duration.ofSeconds(5), clock);
    breaker.onFailure();
    clock.now = clock.now.plusSeconds(6);
    assertTrue(breaker.tryAcquire());
    assertFalse(breaker.tryAcquire(), "only one trial call while half-open");
    breaker.onFailure();
    assertEquals(CircuitBreaker.State.OPEN, breaker.state());
  }

  @Test
  void oneFailingConnectorNeverAffectsTheOthers() {
    var clock = new MutableClock();
    var registry = new ConnectorRegistry();
    registry.register(
        runtime(connector("es", EnumSet.allOf(Access.class), false), clock, new ArrayList<>()));
    registry.register(
        runtime(connector("pg", EnumSet.of(Access.READ), true), clock, new ArrayList<>()));
    registry.register(
        runtime(connector("mongo", EnumSet.allOf(Access.class), false), clock, new ArrayList<>()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            registry.register(runtime(connector("es", Set.of(), false), clock, new ArrayList<>())));
    var states = registry.startAll(Map.of("es", valid("es"), "pg", valid("pg")), SECRETS);
    assertEquals(
        Map.of(
            "es",
            ConnectorState.READY,
            "pg",
            ConnectorState.FAILED,
            "mongo",
            ConnectorState.DISABLED),
        states);
    assertInstanceOf(
        ConnectorResult.Success.class,
        registry.get("es").orElseThrow().execute(Access.READ, RetryPolicy.NONE, () -> "fine"));
    assertEquals(
        ConnectorResult.Failure.NOT_READY,
        ((ConnectorResult.Failed<?>)
                registry.get("pg").orElseThrow().execute(Access.READ, RetryPolicy.NONE, () -> 1))
            .failure());
    registry.stopAll();
    assertEquals(ConnectorState.STOPPED, registry.get("es").orElseThrow().state());
    assertEquals(ConnectorState.DISABLED, registry.get("mongo").orElseThrow().state());
  }
}

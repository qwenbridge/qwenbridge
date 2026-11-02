package io.qwenbridge.core.schema;

import static io.qwenbridge.core.queryir.Constraint.Operator.EQ;
import static io.qwenbridge.core.queryir.Constraint.Operator.NE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.qwenbridge.core.schema.SchemaRegistryException.Code;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/** QB-043, QB-046, QB-047, QB-048, QB-049 — no database, cache or network involved. */
class SchemaRegistryTest {

  private final SchemaRegistry registry = new SchemaRegistry();

  private static Code code(Runnable action) {
    return assertThrows(SchemaRegistryException.class, action::run).code();
  }

  private static DomainSchema withFields(
      DomainSchema base, String version, UnaryOperator<Map<String, FieldDefinition>> change) {
    return new DomainSchema(
        base.id(),
        SchemaVersion.parse(version).orElseThrow(),
        change.apply(new HashMap<>(base.fields())),
        base.entityTypes(),
        base.capabilities());
  }

  @Test
  void registersAndLooksUpByIdAndVersionWithoutExternalServices() {
    DomainSchema commerce = Schemas.commerce("1.0");
    registry.register("pack:commerce", commerce);
    registry.register("pack:devops", Schemas.devops());
    registry.register("pack:security", Schemas.security());

    assertEquals(
        commerce, registry.get(new SchemaKey("commerce", new SchemaVersion(1, 0))).orElseThrow());
    assertEquals(Optional.of("pack:commerce"), registry.owner("commerce"));
    assertEquals(3, registry.keys().size());
    assertTrue(registry.get(new SchemaKey("commerce", new SchemaVersion(9, 0))).isEmpty());
    assertTrue(registry.latest("unknown").isEmpty());
  }

  @Test
  void identicalReRegistrationIsIdempotentButChangedContentIsRejected() {
    DomainSchema v1 = Schemas.commerce("1.0");
    registry.register("pack:commerce", v1);
    assertSame(
        v1,
        registry.register("pack:commerce", Schemas.commerce("1.0")),
        "returns the stored version");
    assertEquals(
        Code.IMMUTABLE_VERSION,
        code(
            () ->
                registry.register(
                    "pack:commerce",
                    withFields(
                        v1,
                        "1.0",
                        f -> {
                          f.remove("color");
                          return f;
                        }))));
  }

  @Test
  void additiveMinorVersionsAreAcceptedAndLatestWins() {
    DomainSchema v10 = Schemas.commerce("1.0");
    DomainSchema v11 =
        withFields(
            v10,
            "1.1",
            f -> {
              f.put("brand", FieldDefinition.of("brand", FieldType.TEXT, Set.of(EQ, NE)));
              return f;
            });
    registry.register("pack:commerce", v10);
    registry.register("pack:commerce", v11);
    assertEquals(v11, registry.latest("commerce").orElseThrow());
    assertEquals(
        List.of(new SchemaVersion(1, 0), new SchemaVersion(1, 1)), registry.versions("commerce"));
  }

  @Test
  void breakingMinorVersionsAreRejected() {
    DomainSchema v10 = Schemas.commerce("1.0");
    registry.register("pack:commerce", v10);

    assertEquals(
        Code.INCOMPATIBLE_CHANGE,
        code(
            () ->
                registry.register(
                    "pack:commerce",
                    withFields(
                        v10,
                        "1.1",
                        f -> {
                          f.remove("color");
                          return f;
                        }))),
        "field removed");
    assertEquals(
        Code.INCOMPATIBLE_CHANGE,
        code(
            () ->
                registry.register(
                    "pack:commerce",
                    withFields(
                        v10,
                        "1.1",
                        f -> {
                          f.put(
                              "color",
                              FieldDefinition.of("color", FieldType.IDENTIFIER, Set.of(EQ)));
                          return f;
                        }))),
        "type changed");
    assertEquals(
        Code.INCOMPATIBLE_CHANGE,
        code(
            () ->
                registry.register(
                    "pack:commerce",
                    withFields(
                        v10,
                        "1.1",
                        f -> {
                          f.put("color", FieldDefinition.of("color", FieldType.TEXT, Set.of(EQ)));
                          return f;
                        }))),
        "operators removed");
    assertEquals(
        Code.INCOMPATIBLE_CHANGE,
        code(
            () ->
                registry.register(
                    "pack:commerce",
                    withFields(
                        v10,
                        "1.1",
                        f -> {
                          f.put(
                              "color",
                              new FieldDefinition(
                                  "color",
                                  FieldType.TEXT,
                                  Set.of(
                                      EQ,
                                      NE,
                                      io.qwenbridge.core.queryir.Constraint.Operator.IN,
                                      io.qwenbridge.core.queryir.Constraint.Operator.NOT_IN),
                                  false,
                                  Optional.empty(),
                                  Optional.empty(),
                                  Optional.empty(),
                                  Set.of("black")));
                          return f;
                        }))),
        "vocabulary narrowed");
    assertEquals(
        Code.INCOMPATIBLE_CHANGE,
        code(
            () ->
                registry.register(
                    "pack:commerce",
                    new DomainSchema(
                        "commerce",
                        new SchemaVersion(1, 1),
                        v10.fields(),
                        Set.of("category"),
                        v10.capabilities()))),
        "entity type removed");
  }

  @Test
  void numericBoundsMayWidenButNotNarrowWithinAMajor() {
    DomainSchema base = Schemas.devops();
    registry.register("pack:devops", base);
    FieldDefinition original = base.fields().get("memory_usage");
    assertEquals(
        Code.INCOMPATIBLE_CHANGE,
        code(
            () ->
                registry.register(
                    "pack:devops",
                    withFields(
                        base,
                        "1.1",
                        f -> {
                          f.put(
                              "memory_usage",
                              new FieldDefinition(
                                  "memory_usage",
                                  FieldType.QUANTITY,
                                  original.operators(),
                                  false,
                                  Optional.of("%"),
                                  Optional.of(BigDecimal.ONE),
                                  original.max(),
                                  Set.of()));
                          return f;
                        }))));
    assertEquals(
        Code.INCOMPATIBLE_CHANGE,
        code(
            () ->
                registry.register(
                    "pack:devops",
                    withFields(
                        base,
                        "1.1",
                        f -> {
                          f.put(
                              "memory_usage",
                              new FieldDefinition(
                                  "memory_usage",
                                  FieldType.QUANTITY,
                                  original.operators(),
                                  false,
                                  Optional.of("MiB"),
                                  Optional.empty(),
                                  Optional.empty(),
                                  Set.of()));
                          return f;
                        }))),
        "unit changed");
  }

  @Test
  void aNewMajorMayBreakCompatibility() {
    DomainSchema v10 = Schemas.commerce("1.0");
    registry.register("pack:commerce", v10);
    DomainSchema v20 =
        withFields(
            v10,
            "2.0",
            f -> {
              f.remove("color");
              return f;
            });
    registry.register("pack:commerce", v20);
    assertEquals(v20, registry.latest("commerce").orElseThrow());
    assertTrue(registry.get(v10.key()).isPresent(), "older versions remain available");
  }

  @Test
  void minorsWithinAMajorMustArriveInOrder() {
    DomainSchema v10 = Schemas.commerce("1.0");
    registry.register("pack:commerce", v10);
    registry.register("pack:commerce", withFields(v10, "1.2", f -> f));
    assertEquals(
        Code.INCOMPATIBLE_CHANGE,
        code(() -> registry.register("pack:commerce", withFields(v10, "1.1", f -> f))));
  }

  @Test
  void ownersCannotTouchSchemasOfOtherOwners() {
    registry.register("pack:commerce", Schemas.commerce("1.0"));
    assertEquals(
        Code.OWNERSHIP_VIOLATION,
        code(() -> registry.register("pack:evil", Schemas.commerce("1.1"))));
    assertEquals(
        Code.OWNERSHIP_VIOLATION,
        code(() -> registry.register("pack:evil", Schemas.commerce("1.0"))));
    assertEquals(
        Code.OWNERSHIP_VIOLATION,
        code(
            () ->
                registry.unregister(
                    "pack:evil", new SchemaKey("commerce", new SchemaVersion(1, 0)))));
    assertTrue(registry.unregisterAll("pack:evil").isEmpty());
    assertTrue(registry.latest("commerce").isPresent());
  }

  @Test
  void deactivatingAPackRemovesOnlyItsOwnSchemas() {
    registry.register("pack:commerce", Schemas.commerce("1.0"));
    registry.register("pack:commerce", Schemas.commerce("1.1"));
    registry.register("pack:devops", Schemas.devops());

    List<SchemaKey> removed = registry.unregisterAll("pack:commerce");

    assertEquals(2, removed.size());
    assertTrue(registry.latest("commerce").isEmpty());
    assertTrue(registry.latest("devops").isPresent());
    registry.register("pack:other", Schemas.commerce("1.0")); // the id is free again
  }

  @Test
  void invalidOwnersAndMissingKeysAreTypedErrors() {
    assertEquals(
        Code.INVALID_OWNER,
        code(() -> registry.register("Pack Commerce", Schemas.commerce("1.0"))));
    assertEquals(Code.INVALID_OWNER, code(() -> registry.register(null, Schemas.commerce("1.0"))));
    assertEquals(
        Code.NOT_FOUND,
        code(() -> registry.unregister("core", new SchemaKey("nope", new SchemaVersion(1, 0)))));
  }

  @Test
  void schemaContractRejectsInconsistentDefinitions() {
    assertThrows(
        IllegalArgumentException.class,
        () -> FieldDefinition.of("x", FieldType.QUANTITY, Set.of(EQ)));
    assertThrows(
        IllegalArgumentException.class, () -> FieldDefinition.of("x", FieldType.TEXT, Set.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new FieldDefinition(
                "x",
                FieldType.TEXT,
                Set.of(EQ),
                false,
                Optional.empty(),
                Optional.of(BigDecimal.ONE),
                Optional.empty(),
                Set.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new FieldDefinition(
                "x",
                FieldType.TEXT,
                Set.of(io.qwenbridge.core.queryir.Constraint.Operator.EXISTS),
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Set.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new DomainSchema(
                "Commerce",
                new SchemaVersion(1, 0),
                Schemas.commerce("1.0").fields(),
                Set.of(),
                Set.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new DomainSchema(
                "x",
                new SchemaVersion(1, 0),
                Map.of("a", FieldDefinition.of("b", FieldType.TEXT, Set.of(EQ))),
                Set.of(),
                Set.of()));
    assertTrue(SchemaVersion.parse("0.1").isEmpty());
    assertTrue(SchemaVersion.parse("1.x").isEmpty());
  }

  // --- Concurrency (QB-043) --------------------------------------------------------------

  @Test
  void concurrentRegistrationsAndReadsLoseNothing() throws Exception {
    int threads = 16;
    int perThread = 25;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>();
    for (int t = 0; t < threads; t++) {
      int id = t;
      futures.add(
          pool.submit(
              () -> {
                start.await();
                for (int v = 0; v < perThread; v++) {
                  DomainSchema schema =
                      new DomainSchema(
                          "domain-" + id,
                          new SchemaVersion(1, v),
                          Map.of("f", FieldDefinition.of("f", FieldType.TEXT, Set.of(EQ))),
                          Set.of(),
                          Set.of());
                  registry.register("pack:p" + id, schema);
                  registry.keys(); // concurrent reads must never fail or see partial state
                  assertEquals(schema, registry.latest("domain-" + id).orElseThrow());
                }
                return null;
              }));
    }
    start.countDown();
    for (Future<?> f : futures) {
      f.get(30, TimeUnit.SECONDS);
    }
    pool.shutdown();
    assertEquals(threads * perThread, registry.keys().size());
  }

  @Test
  void racingConflictingRegistrationsHaveExactlyOneWinner() throws Exception {
    int threads = 12;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger winners = new AtomicInteger();
    Map<Code, AtomicInteger> failures = new ConcurrentHashMap<>();
    List<Future<?>> futures = new ArrayList<>();
    for (int t = 0; t < threads; t++) {
      int id = t;
      futures.add(
          pool.submit(
              () -> {
                start.await();
                try {
                  registry.register("pack:racer" + id, Schemas.commerce("1.0"));
                  winners.incrementAndGet();
                } catch (SchemaRegistryException e) {
                  failures.computeIfAbsent(e.code(), c -> new AtomicInteger()).incrementAndGet();
                }
                return null;
              }));
    }
    start.countDown();
    for (Future<?> f : futures) {
      f.get(30, TimeUnit.SECONDS);
    }
    pool.shutdown();
    assertEquals(1, winners.get(), "exactly one owner may claim a schema id");
    assertEquals(threads - 1, failures.get(Code.OWNERSHIP_VIOLATION).get());
  }
}

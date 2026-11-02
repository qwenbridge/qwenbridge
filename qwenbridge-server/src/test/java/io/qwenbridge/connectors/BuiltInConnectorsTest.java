package io.qwenbridge.connectors;

import static org.assertj.core.api.Assertions.*;

import io.qwenbridge.core.connector.ConnectorCapabilities.Access;
import io.qwenbridge.execution.provider.model.SearchResultSet;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** QB-153 – QB-159 for the built-in connectors: lifecycle, guard, isolation. */
class BuiltInConnectorsTest {

  static CommerceBackend backend(String id, boolean configured, Supplier<SearchResultSet> search) {
    return new CommerceBackend() {
      public String id() {
        return id;
      }

      public Set<String> modes() {
        return Set.of("KEYWORD");
      }

      public boolean configured() {
        return configured;
      }

      public SearchResultSet search(Query query) {
        return search.get();
      }

      public void upsert(String tenant, String docId, Map<String, Object> document) {}

      public void delete(String tenant, String docId) {}
    };
  }

  @Test
  void repeatedFailuresOpenTheCircuitAndTheBackendIsNoLongerCalled() {
    var calls = new AtomicInteger();
    var down =
        backend(
            "es",
            true,
            () -> {
              calls.incrementAndGet();
              throw new UncheckedIOException(new java.io.IOException("connection refused"));
            });
    var healthy = backend("memory", true, SearchResultSet::empty);
    var connectors =
        new BuiltInConnectors(
            List.of(down, healthy), 2, Duration.ofMinutes(5), Duration.ofSeconds(5));
    for (int i = 0; i < 2; i++)
      assertThatThrownBy(() -> connectors.read("es", () -> down.search(null)))
          .isInstanceOf(UncheckedIOException.class);
    // One retry per call for an I/O failure: 2 calls × 2 attempts.
    assertThat(calls.get()).isEqualTo(4);
    assertThatThrownBy(() -> connectors.read("es", () -> down.search(null)))
        .isInstanceOf(jakarta.ws.rs.ServiceUnavailableException.class)
        .hasMessageContaining("CIRCUIT_OPEN");
    assertThat(calls.get()).isEqualTo(4);
    // The other connector is unaffected.
    assertThat(connectors.read("memory", () -> healthy.search(null)).hits()).isEmpty();
    var views = connectors.views();
    assertThat(views)
        .filteredOn(v -> v.id().equals("es"))
        .singleElement()
        .satisfies(
            v -> {
              assertThat(v.state()).isEqualTo("DEGRADED");
              assertThat(v.circuit()).isEqualTo("OPEN");
            });
    assertThat(views)
        .filteredOn(v -> v.id().equals("memory"))
        .singleElement()
        .satisfies(v -> assertThat(v.state()).isEqualTo("READY"));
  }

  @Test
  void callerErrorsPassThroughAndNeverOpenTheCircuit() {
    var unsupported =
        backend(
            "pg",
            true,
            () -> {
              throw new jakarta.ws.rs.ClientErrorException(
                  "Backend pg does not support VECTOR", 422);
            });
    var connectors =
        new BuiltInConnectors(
            List.of(unsupported), 1, Duration.ofMinutes(5), Duration.ofSeconds(5));
    for (int i = 0; i < 5; i++)
      assertThatThrownBy(() -> connectors.read("pg", () -> unsupported.search(null)))
          .isInstanceOf(jakarta.ws.rs.ClientErrorException.class)
          .extracting(e -> ((jakarta.ws.rs.ClientErrorException) e).getResponse().getStatus())
          .isEqualTo(422);
    assertThat(connectors.views().getFirst().circuit()).isEqualTo("CLOSED");
    var invalid =
        backend(
            "mongo",
            true,
            () -> {
              throw new IllegalArgumentException("Invalid cursor");
            });
    var other =
        new BuiltInConnectors(List.of(invalid), 1, Duration.ofMinutes(5), Duration.ofSeconds(5));
    assertThatThrownBy(() -> other.read("mongo", () -> invalid.search(null)))
        .hasMessage("Invalid cursor");
    assertThat(other.views().getFirst().state()).isEqualTo("READY");
  }

  @Test
  void unconfiguredConnectorsAreDisabledAndWritesAreNotRetried() {
    var off = backend("elasticsearch", false, SearchResultSet::empty);
    var connectors =
        new BuiltInConnectors(List.of(off), 3, Duration.ofMinutes(5), Duration.ofSeconds(5));
    var view = connectors.views().getFirst();
    assertThat(view.state()).isEqualTo("DISABLED");
    assertThat(view.access()).containsExactly("DELETE", "READ", "WRITE");
    assertThat(view.configuration()).isEqualTo("connector-config/1");
    assertThatThrownBy(() -> connectors.read("elasticsearch", SearchResultSet::empty))
        .isInstanceOf(jakarta.ws.rs.ServiceUnavailableException.class)
        .hasMessageContaining("NOT_READY");
    var writes = new AtomicInteger();
    var flaky = backend("os", true, SearchResultSet::empty);
    var w = new BuiltInConnectors(List.of(flaky), 3, Duration.ofMinutes(5), Duration.ofSeconds(5));
    assertThatThrownBy(
            () ->
                w.write(
                    "os",
                    Access.WRITE,
                    () -> {
                      writes.incrementAndGet();
                      throw new UncheckedIOException(new java.io.IOException("reset"));
                    }))
        .isInstanceOf(UncheckedIOException.class);
    assertThat(writes.get()).as("a write that may have happened is never repeated").isEqualTo(1);
  }

  @Test
  void readOnlyBackendsRefuseWritesBeforeTheyAreCalled() {
    var calls = new AtomicInteger();
    var readOnly =
        new CommerceBackend() {
          public String id() {
            return "postgresql";
          }

          public Set<String> modes() {
            return Set.of("KEYWORD");
          }

          public boolean configured() {
            return true;
          }

          public boolean readOnly() {
            return true;
          }

          public SearchResultSet search(Query query) {
            return SearchResultSet.empty();
          }

          public void upsert(String tenant, String docId, Map<String, Object> document) {
            calls.incrementAndGet();
          }

          public void delete(String tenant, String docId) {
            calls.incrementAndGet();
          }
        };
    var connectors =
        new BuiltInConnectors(List.of(readOnly), 3, Duration.ofMinutes(5), Duration.ofSeconds(5));
    assertThat(connectors.views().getFirst().access()).containsExactly("READ");
    assertThatThrownBy(
            () ->
                connectors.write(
                    "postgresql",
                    Access.WRITE,
                    () -> {
                      readOnly.upsert("t", "d", Map.of());
                      return 1;
                    }))
        .isInstanceOf(jakarta.ws.rs.ForbiddenException.class)
        .hasMessageContaining("does not allow write");
    assertThatThrownBy(
            () ->
                connectors.write(
                    "postgresql",
                    Access.DELETE,
                    () -> {
                      readOnly.delete("t", "d");
                      return 1;
                    }))
        .isInstanceOf(jakarta.ws.rs.ForbiddenException.class);
    assertThat(calls.get()).isZero();
    assertThat(connectors.read("postgresql", SearchResultSet::empty).hits()).isEmpty();
  }
}

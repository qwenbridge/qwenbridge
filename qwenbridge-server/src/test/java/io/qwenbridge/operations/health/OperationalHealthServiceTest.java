package io.qwenbridge.operations.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OperationalHealthServiceTest {

  @Test
  void shouldReturnUpWhenAllDependenciesAreUp() {
    OperationalHealthService service =
        new OperationalHealthService(List.of(() -> DependencyHealth.up("redis", 1)), "qwenbridge");

    ReadinessHealthResponse response = service.readiness();

    assertThat(response.status()).isEqualTo(OperationalStatus.UP);
    assertThat(response.dependencies()).hasSize(1);
  }

  @Test
  void shouldReturnDegradedWhenAnyDependencyIsDegraded() {
    OperationalHealthService service =
        new OperationalHealthService(
            List.of(
                () -> DependencyHealth.up("redis", 1),
                () -> DependencyHealth.degraded("ollama", "unavailable", 2)),
            "qwenbridge");

    ReadinessHealthResponse response = service.readiness();

    assertThat(response.status()).isEqualTo(OperationalStatus.DEGRADED);
    assertThat(response.dependencies())
        .extracting(DependencyHealth::reason)
        .contains("unavailable");
  }

  @Test
  void shouldReturnDownWhenAnyDependencyIsDown() {
    OperationalHealthService service =
        new OperationalHealthService(
            List.of(() -> DependencyHealth.down("opensearch", "unavailable", 3)), "qwenbridge");

    ReadinessHealthResponse response = service.readiness();

    assertThat(response.status()).isEqualTo(OperationalStatus.DOWN);
  }

  @Test
  void anUnselectedOptionalDependencyIsNeitherProbedNorReported() {
    var probes = new java.util.concurrent.atomic.AtomicInteger();
    DependencyHealthChecker unselected =
        new DependencyHealthChecker() {
          @Override
          public DependencyHealth check() {
            probes.incrementAndGet();
            return DependencyHealth.down("opensearch", "unavailable", 3);
          }

          @Override
          public boolean active() {
            return false;
          }
        };
    var response = new OperationalHealthService(List.of(unselected), "qwenbridge").readiness();
    assertThat(response.status()).isEqualTo(OperationalStatus.UP);
    assertThat(response.dependencies()).isEmpty();
    assertThat(probes.get()).isZero();
  }

  @Test
  void openSearchIsActiveOnlyWhenSelected() {
    assertThat(new OpenSearchDependencyHealthChecker(null, "in-memory", "memory").active())
        .isFalse();
    assertThat(new OpenSearchDependencyHealthChecker(null, "opensearch", "memory").active())
        .isTrue();
    assertThat(new OpenSearchDependencyHealthChecker(null, "in-memory", "opensearch").active())
        .isTrue();
  }
}

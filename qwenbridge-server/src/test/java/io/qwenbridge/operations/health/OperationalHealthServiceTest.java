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
}

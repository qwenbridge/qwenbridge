package io.qwenbridge.operations.health;

import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class OperationalHealthService {

  private final List<DependencyHealthChecker> checkers;
  private final String applicationName;

  @Inject
  public OperationalHealthService(
      @All List<DependencyHealthChecker> checkers,
      @ConfigProperty(name = "quarkus.application.name", defaultValue = "qwenbridge")
          String applicationName) {
    this.checkers = checkers;
    this.applicationName = applicationName;
  }

  public ReadinessHealthResponse readiness() {
    List<DependencyHealth> dependencies =
        checkers.stream()
            .map(DependencyHealthChecker::check)
            .sorted(Comparator.comparing(DependencyHealth::name))
            .toList();

    OperationalStatus status = aggregate(dependencies);

    return ReadinessHealthResponse.builder()
        .status(status)
        .service(applicationName)
        .apiVersion("v1")
        .checkedAt(Instant.now())
        .dependencies(dependencies)
        .build();
  }

  private OperationalStatus aggregate(List<DependencyHealth> dependencies) {
    if (dependencies.stream()
        .anyMatch(dependency -> dependency.status() == OperationalStatus.DOWN)) {
      return OperationalStatus.DOWN;
    }
    if (dependencies.stream()
        .anyMatch(dependency -> dependency.status() == OperationalStatus.DEGRADED)) {
      return OperationalStatus.DEGRADED;
    }
    return OperationalStatus.UP;
  }
}

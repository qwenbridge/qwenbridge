package io.qwenbridge.operations.health;

import java.time.Instant;
import java.util.List;
import lombok.Builder;

@Builder
public record ReadinessHealthResponse(
    OperationalStatus status,
    String service,
    String apiVersion,
    Instant checkedAt,
    List<DependencyHealth> dependencies) {}

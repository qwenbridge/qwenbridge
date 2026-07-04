package io.qwenbridge.operations.health;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
public record ReadinessHealthResponse(
        OperationalStatus status,
        String service,
        String apiVersion,
        Instant checkedAt,
        List<DependencyHealth> dependencies
) {
}

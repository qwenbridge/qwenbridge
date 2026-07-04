package io.qwenbridge.operations.health;

import lombok.Builder;

@Builder
public record DependencyHealth(
        String name,
        OperationalStatus status,
        String reason,
        long durationMs
) {
    public static DependencyHealth up(String name, long durationMs) {
        return DependencyHealth.builder().name(name).status(OperationalStatus.UP).reason("available").durationMs(durationMs).build();
    }

    public static DependencyHealth degraded(String name, String reason, long durationMs) {
        return DependencyHealth.builder().name(name).status(OperationalStatus.DEGRADED).reason(reason).durationMs(durationMs).build();
    }

    public static DependencyHealth down(String name, String reason, long durationMs) {
        return DependencyHealth.builder().name(name).status(OperationalStatus.DOWN).reason(reason).durationMs(durationMs).build();
    }
}

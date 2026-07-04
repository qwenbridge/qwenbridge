package io.qwenbridge.starter.health;

import io.qwenbridge.starter.QwenBridgeProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.Objects;

public final class QwenBridgeHealthIndicator implements HealthIndicator {

    private final QwenBridgeProperties properties;

    public QwenBridgeHealthIndicator(QwenBridgeProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public Health health() {
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            return Health.down()
                    .withDetail("reason", "qwenbridge.base-url is not configured")
                    .build();
        }

        return Health.up()
                .withDetail("baseUrl", properties.getBaseUrl())
                .withDetail("mode", "configuration-only")
                .build();
    }
}

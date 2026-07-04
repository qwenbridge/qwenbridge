package io.qwenbridge.sdk.config;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

public record QwenBridgeClientConfig(
        URI baseUrl,
        Duration connectTimeout,
        Duration requestTimeout
) {

    public QwenBridgeClientConfig {
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        Objects.requireNonNull(connectTimeout, "connectTimeout must not be null");
        Objects.requireNonNull(requestTimeout, "requestTimeout must not be null");
    }

    public static QwenBridgeClientConfig localDefault() {
        return new QwenBridgeClientConfig(
                URI.create("http://localhost:8080"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(30)
        );
    }
}

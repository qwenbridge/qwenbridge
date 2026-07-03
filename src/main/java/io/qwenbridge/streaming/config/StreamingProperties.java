package io.qwenbridge.streaming.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qwenbridge.streaming")
public record StreamingProperties(
        long sessionTimeoutMs
) {
    public StreamingProperties {
        if (sessionTimeoutMs <= 0) {
            sessionTimeoutMs = 300_000L;
        }
    }
}

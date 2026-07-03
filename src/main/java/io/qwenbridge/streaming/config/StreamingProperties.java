package io.qwenbridge.streaming.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "qwenbridge.streaming")
public record StreamingProperties(
        long sessionTimeoutMs,
        Duration maxAiStreamDuration,
        long maxAiTokenCount,
        long maxAiEventCount
) {
    public StreamingProperties {
        if (sessionTimeoutMs <= 0) {
            sessionTimeoutMs = 300_000L;
        }
        if (maxAiStreamDuration == null || maxAiStreamDuration.isNegative() || maxAiStreamDuration.isZero()) {
            maxAiStreamDuration = Duration.ofSeconds(30);
        }
        if (maxAiTokenCount <= 0) {
            maxAiTokenCount = 1_000L;
        }
        if (maxAiEventCount <= 0) {
            maxAiEventCount = 1_100L;
        }
    }
}

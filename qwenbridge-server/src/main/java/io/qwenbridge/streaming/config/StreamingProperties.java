package io.qwenbridge.streaming.config;

import java.time.Duration;

public record StreamingProperties(
    long sessionTimeoutMs,
    Duration maxAiStreamDuration,
    long maxAiTokenCount,
    long maxAiEventCount) {
  public StreamingProperties {
    if (sessionTimeoutMs <= 0) {
      sessionTimeoutMs = 300_000L;
    }
    if (maxAiStreamDuration == null
        || maxAiStreamDuration.isNegative()
        || maxAiStreamDuration.isZero()) {
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

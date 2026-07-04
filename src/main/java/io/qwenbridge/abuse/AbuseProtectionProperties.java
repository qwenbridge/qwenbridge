package io.qwenbridge.abuse;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "qwenbridge.abuse")
public record AbuseProtectionProperties(
        boolean enabled,
        int requestSizeLimitBytes,
        int perIpLimit,
        int perApiKeyLimit,
        int aiRequestQuota,
        int tokenQuota,
        int concurrentStreamLimit,
        Duration window,
        Duration redisTimeout,
        boolean failOpenWhenRedisUnavailable
) {
    public AbuseProtectionProperties {
        if (requestSizeLimitBytes <= 0) requestSizeLimitBytes = 65_536;
        if (perIpLimit <= 0) perIpLimit = 60;
        if (perApiKeyLimit <= 0) perApiKeyLimit = 600;
        if (aiRequestQuota <= 0) aiRequestQuota = 120;
        if (tokenQuota <= 0) tokenQuota = 100_000;
        if (concurrentStreamLimit <= 0) concurrentStreamLimit = 25;
        if (window == null) window = Duration.ofMinutes(1);
        if (redisTimeout == null) redisTimeout = Duration.ofSeconds(1);
    }
}

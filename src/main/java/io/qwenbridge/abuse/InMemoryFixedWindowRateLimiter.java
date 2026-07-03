package io.qwenbridge.abuse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class InMemoryFixedWindowRateLimiter implements RateLimiter {

    private final AbuseProtectionProperties properties;
    private final Clock clock;
    private final ConcurrentMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Autowired
    public InMemoryFixedWindowRateLimiter(AbuseProtectionProperties properties) {
        this(properties, Clock.systemUTC());
    }

    InMemoryFixedWindowRateLimiter(AbuseProtectionProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public RateLimitDecision consume(String policy, String subject, long limit, long cost) {
        long now = clock.millis();
        long windowMs = properties.window().toMillis();
        long windowStart = (now / windowMs) * windowMs;
        String key = policy + ':' + subject + ':' + windowStart;
        counters.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);

        WindowCounter counter = counters.computeIfAbsent(
                key,
                ignored -> new WindowCounter(windowStart + windowMs, new AtomicLong())
        );
        long value = counter.count().addAndGet(cost);
        Instant resetAt = Instant.ofEpochMilli(counter.expiresAtMillis());

        if (value > limit) {
            return RateLimitDecision.rejected(policy, limit, resetAt);
        }

        return RateLimitDecision.allowed(policy, limit, limit - value, resetAt);
    }

    private record WindowCounter(long expiresAtMillis, AtomicLong count) {
    }
}

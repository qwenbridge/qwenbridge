package io.qwenbridge.abuse;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Primary
@ConditionalOnBean(StringRedisTemplate.class)
@Slf4j
public class RedisBackedRateLimiter implements RateLimiter {

  private final StringRedisTemplate redis;
  private final InMemoryFixedWindowRateLimiter fallback;
  private final AbuseProtectionProperties properties;
  private final Clock clock;

  public RedisBackedRateLimiter(
      StringRedisTemplate redis,
      InMemoryFixedWindowRateLimiter fallback,
      AbuseProtectionProperties properties) {
    this.redis = redis;
    this.fallback = fallback;
    this.properties = properties;
    this.clock = Clock.systemUTC();
  }

  @Override
  public RateLimitDecision consume(String policy, String subject, long limit, long cost) {
    long now = clock.millis();
    long windowMs = properties.window().toMillis();
    long windowStart = (now / windowMs) * windowMs;
    long expiresAt = windowStart + windowMs;
    String key = "qwenbridge:rate-limit:" + policy + ':' + subject + ':' + windowStart;

    try {
      Long value = redis.opsForValue().increment(key, cost);
      if (value != null && value == cost) {
        redis.expire(key, windowMs, TimeUnit.MILLISECONDS);
      }

      if (value != null && value > limit) {
        return RateLimitDecision.rejected(policy, limit, Instant.ofEpochMilli(expiresAt));
      }

      return RateLimitDecision.allowed(
          policy, limit, limit - (value == null ? cost : value), Instant.ofEpochMilli(expiresAt));
    } catch (RuntimeException ex) {
      log.warn(
          "Redis rate limiter unavailable; using {} fallback",
          properties.failOpenWhenRedisUnavailable() ? "fail-open" : "in-memory",
          ex);
      if (properties.failOpenWhenRedisUnavailable()) {
        return RateLimitDecision.allowed(policy, limit, limit, Instant.ofEpochMilli(expiresAt));
      }
      return fallback.consume(policy, subject, limit, cost);
    }
  }
}

package io.qwenbridge.abuse;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@jakarta.enterprise.context.ApplicationScoped
@Slf4j
public class RedisBackedRateLimiter implements RateLimiter {

  private final RedisDataSource redis;
  private final ValueCommands<String, String> values;
  private final InMemoryFixedWindowRateLimiter fallback;
  private final AbuseProtectionProperties properties;
  private final boolean redisEnabled;
  private final Clock clock;

  public RedisBackedRateLimiter(
      RedisDataSource redis,
      InMemoryFixedWindowRateLimiter fallback,
      AbuseProtectionProperties properties,
      @ConfigProperty(name = "qwenbridge.abuse-protection.redis-enabled", defaultValue = "true")
          boolean redisEnabled) {
    this.redis = redis;
    this.values = redis.value(String.class);
    this.fallback = fallback;
    this.properties = properties;
    this.redisEnabled = redisEnabled;
    this.clock = Clock.systemUTC();
  }

  @Override
  public RateLimitDecision consume(String policy, String subject, long limit, long cost) {
    long now = clock.millis();
    long windowMs = properties.window().toMillis();
    long windowStart = (now / windowMs) * windowMs;
    long expiresAt = windowStart + windowMs;
    String key = "qwenbridge:rate-limit:" + policy + ':' + subject + ':' + windowStart;

    if (!redisEnabled) {
      return fallback.consume(policy, subject, limit, cost);
    }

    try {
      long value = values.incrby(key, cost);
      if (value == cost) {
        redis.key().expire(key, Duration.ofMillis(windowMs));
      }

      if (value > limit) {
        return RateLimitDecision.rejected(policy, limit, Instant.ofEpochMilli(expiresAt));
      }

      return RateLimitDecision.allowed(
          policy, limit, limit - value, Instant.ofEpochMilli(expiresAt));
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

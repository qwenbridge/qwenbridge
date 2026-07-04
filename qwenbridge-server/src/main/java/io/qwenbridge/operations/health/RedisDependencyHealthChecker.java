package io.qwenbridge.operations.health;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component
public class RedisDependencyHealthChecker implements DependencyHealthChecker {

    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactory;

    public RedisDependencyHealthChecker(ObjectProvider<RedisConnectionFactory> redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Override
    public DependencyHealth check() {
        long started = System.nanoTime();
        RedisConnectionFactory factory = redisConnectionFactory.getIfAvailable();
        if (factory == null) {
            return DependencyHealth.degraded("redis", "not_configured", durationMs(started));
        }
        try (RedisConnection connection = factory.getConnection()) {
            String pong = connection.ping();
            if (pong == null || pong.isBlank()) {
                return DependencyHealth.degraded("redis", "empty_ping_response", durationMs(started));
            }
            return DependencyHealth.up("redis", durationMs(started));
        } catch (Exception ex) {
            return DependencyHealth.degraded("redis", "unavailable", durationMs(started));
        }
    }

    private long durationMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }
}

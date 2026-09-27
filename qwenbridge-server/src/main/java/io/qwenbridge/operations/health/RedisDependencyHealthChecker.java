package io.qwenbridge.operations.health;

import io.quarkus.redis.datasource.RedisDataSource;
import io.vertx.mutiny.redis.client.Response;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RedisDependencyHealthChecker implements DependencyHealthChecker {

  private final RedisDataSource redisDataSource;

  public RedisDependencyHealthChecker(RedisDataSource redisDataSource) {
    this.redisDataSource = redisDataSource;
  }

  @Override
  public DependencyHealth check() {
    long started = System.nanoTime();
    try {
      Response pong = redisDataSource.execute("PING");
      if (pong == null || pong.toString() == null || pong.toString().isBlank()) {
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

package io.qwenbridge.platform;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@QuarkusTest
@EnabledIfEnvironmentVariable(named = "QWENBRIDGE_RUN_REDIS_IT", matches = "true")
class PlatformPersistenceIntegrationTest {
  @Inject RedisDataSource redis;
  @Inject ObjectMapper mapper;

  public record Counter(int version, int count) {}

  @Test
  void independentStoreInstancesShareDurableTenantStateAndAtomicUpdates() throws Exception {
    var one = new PlatformStore(redis, mapper, Optional.of(true));
    var two = new PlatformStore(redis, mapper, Optional.of(true));
    String id = UUID.randomUUID().toString();
    one.update(
        "table1-persistence",
        "checks",
        id,
        Counter.class,
        Duration.ofSeconds(60),
        old -> new Counter(1, 0));
    try {
      assertThat(two.read("table1-persistence", "checks", id, Counter.class))
          .isEqualTo(new Counter(1, 0));
      assertThat(two.read("other-tenant", "checks", id, Counter.class)).isNull();
      try (var executor = Executors.newFixedThreadPool(4)) {
        var tasks =
            java.util.stream.IntStream.range(0, 16)
                .mapToObj(
                    i ->
                        executor.submit(
                            () ->
                                (i % 2 == 0 ? one : two)
                                    .update(
                                        "table1-persistence",
                                        "checks",
                                        id,
                                        Counter.class,
                                        Duration.ofSeconds(60),
                                        old -> new Counter(1, old.count() + 1))))
                .toList();
        for (var task : tasks) task.get(10, TimeUnit.SECONDS);
      }
      assertThat(
              new PlatformStore(redis, mapper, Optional.of(true))
                  .read("table1-persistence", "checks", id, Counter.class)
                  .count())
          .isEqualTo(16);
      assertThat(
              redis
                  .execute("TTL", "qwenbridge:platform:table1-persistence:checks:" + id)
                  .toInteger())
          .isBetween(1, 60);
    } finally {
      redis.key().del("qwenbridge:platform:table1-persistence:checks:" + id);
    }
  }

  @Test
  void theRedisAdapterEnforcesTheSameValueSizeLimitAndWritesNothingOversized() {
    var store = new PlatformStore(redis, mapper, Optional.of(true));
    assertThat(store.adapter()).isEqualTo("redis");
    String id = "oversized-" + java.util.UUID.randomUUID();
    String big = "x".repeat(1_100_000);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                store.update(
                    "table1-persistence",
                    "checks",
                    id,
                    String.class,
                    java.time.Duration.ofSeconds(30),
                    old -> big))
        .isInstanceOf(jakarta.ws.rs.ClientErrorException.class);
    assertThat(store.read("table1-persistence", "checks", id, String.class)).isNull();
  }
}

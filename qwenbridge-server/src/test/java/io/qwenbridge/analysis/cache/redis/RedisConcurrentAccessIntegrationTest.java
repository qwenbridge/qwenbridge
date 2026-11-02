package io.qwenbridge.analysis.cache.redis;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.qwenbridge.analysis.cache.CacheKey;
import io.qwenbridge.analysis.model.SearchAnalysis;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@QuarkusTest
@TestProfile(RedisConcurrentAccessIntegrationTest.RealCache.class)
@EnabledIfEnvironmentVariable(named = "QWENBRIDGE_RUN_REDIS_IT", matches = "true")
class RedisConcurrentAccessIntegrationTest {
  public static class RealCache implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "qwenbridge.analysis.cache.enabled", "true",
          "qwenbridge.analysis.cache.key-prefix", "qwenbridge:verification:redis-pool");
    }
  }

  @Inject RedisAIAnalysisCache cache;

  @Test
  void concurrentReadsMustNotTurnAnExistingEntryIntoFalseMisses() throws Exception {
    CacheKey key = new CacheKey(UUID.randomUUID().toString());
    cache.put(
        key,
        SearchAnalysis.builder()
            .semanticValidated(true)
            .rewrites(java.util.List.of("pool probe"))
            .build());
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      assertThat(cache.get(key)).isPresent();
      int misses = 0;
      for (int round = 0; round < 4; round++) {
        var ready = new CountDownLatch(250);
        var start = new CountDownLatch(1);
        var reads = new ArrayList<Future<Boolean>>();
        for (int index = 0; index < 250; index++) {
          reads.add(
              executor.submit(
                  () -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS))
                      throw new IllegalStateException("Read wave did not start");
                    return cache.get(key).isPresent();
                  }));
        }
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        for (var read : reads) if (!read.get(10, TimeUnit.SECONDS)) misses++;
      }
      assertThat(misses).as("cached reads must survive 250 simultaneous clients").isZero();
    } finally {
      cache.evict(key);
    }
  }
}

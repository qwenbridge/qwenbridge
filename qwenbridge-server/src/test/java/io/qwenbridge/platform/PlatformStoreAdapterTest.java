package io.qwenbridge.platform;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** QB-142, QB-143, QB-146: the memory adapter needs no Redis and enforces the same limits. */
class PlatformStoreAdapterTest {
  @Test
  void memoryAdapterNeedsNoRedisAndBoundsValues() {
    jakarta.enterprise.inject.Instance<io.quarkus.redis.datasource.RedisDataSource> noRedis =
        org.mockito.Mockito.mock(jakarta.enterprise.inject.Instance.class);
    org.mockito.Mockito.when(noRedis.get())
        .thenThrow(new IllegalStateException("Redis must not be resolved by the memory adapter"));
    var store =
        new PlatformStore(
            noRedis, new ObjectMapper(), Optional.of("memory"), Optional.empty(), 4096);
    assertThat(store.adapter()).isEqualTo("memory");
    assertThat(
            store.update("t1", "sessions", "a", String.class, Duration.ofMinutes(1), old -> "v1"))
        .isEqualTo("v1");
    assertThat(store.read("t1", "sessions", "a", String.class)).isEqualTo("v1");
    assertThat(store.read("t2", "sessions", "a", String.class)).isNull();
    assertThatThrownBy(
            () ->
                store.update(
                    "t1",
                    "sessions",
                    "b",
                    String.class,
                    Duration.ofMinutes(1),
                    old -> "x".repeat(5000)))
        .isInstanceOf(jakarta.ws.rs.ClientErrorException.class)
        .hasMessageContaining("size limit");
    assertThat(store.read("t1", "sessions", "b", String.class)).isNull();
    org.mockito.Mockito.verify(noRedis, org.mockito.Mockito.never()).get();
  }

  @Test
  void adapterSelectionIsExplicitAndValidated() {
    jakarta.enterprise.inject.Instance<io.quarkus.redis.datasource.RedisDataSource> redis =
        org.mockito.Mockito.mock(jakarta.enterprise.inject.Instance.class);
    assertThat(
            new PlatformStore(
                    redis, new ObjectMapper(), Optional.of("redis"), Optional.of(false), 4096)
                .adapter())
        .isEqualTo("redis");
    assertThat(
            new PlatformStore(redis, new ObjectMapper(), Optional.empty(), Optional.of(false), 4096)
                .adapter())
        .isEqualTo("memory");
    assertThatThrownBy(
            () ->
                new PlatformStore(
                    redis, new ObjectMapper(), Optional.of("disk"), Optional.empty(), 4096))
        .hasMessageContaining("redis or memory");
    assertThatThrownBy(
            () ->
                new PlatformStore(
                    redis, new ObjectMapper(), Optional.of("memory"), Optional.empty(), 10))
        .hasMessageContaining("max-value-bytes");
  }
}

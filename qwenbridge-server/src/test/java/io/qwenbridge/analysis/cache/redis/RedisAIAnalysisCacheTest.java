package io.qwenbridge.analysis.cache.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.analysis.cache.CacheKey;
import io.qwenbridge.analysis.cache.config.AIAnalysisCacheProperties;
import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.operations.metrics.OperationsMetrics;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import org.junit.jupiter.api.Test;

class RedisAIAnalysisCacheTest {

  @Test
  void shouldReturnCachedAnalysisWhenPayloadExists() throws Exception {
    RedisDataSource redis = mock(RedisDataSource.class);
    ValueCommands<String, String> ops = mock(ValueCommands.class);
    ObjectMapper mapper = new ObjectMapper();

    AIAnalysisCacheProperties properties = redisProperties();

    SearchAnalysis analysis = SearchAnalysis.fallback("desk");
    String payload = mapper.writeValueAsString(analysis);

    when(redis.value(String.class)).thenReturn(ops);
    when(ops.get("test:analysis:key")).thenReturn(payload);

    RedisAIAnalysisCache cache =
        new RedisAIAnalysisCache(redis, mapper, properties, mock(OperationsMetrics.class));

    assertThat(cache.get(new CacheKey("key"))).contains(analysis);
  }

  @Test
  void shouldReturnEmptyWhenRedisFails() {
    RedisDataSource redis = mock(RedisDataSource.class);
    ValueCommands<String, String> ops = mock(ValueCommands.class);
    ObjectMapper mapper = new ObjectMapper();

    AIAnalysisCacheProperties properties = redisProperties();

    when(redis.value(String.class)).thenReturn(ops);
    when(ops.get("test:analysis:key")).thenThrow(new RuntimeException("redis down"));

    RedisAIAnalysisCache cache =
        new RedisAIAnalysisCache(redis, mapper, properties, mock(OperationsMetrics.class));

    assertThat(cache.get(new CacheKey("key"))).isEmpty();
  }

  @Test
  void shouldWritePayloadWithConfiguredTtl() throws Exception {
    RedisDataSource redis = mock(RedisDataSource.class);
    ValueCommands<String, String> ops = mock(ValueCommands.class);
    ObjectMapper mapper = new ObjectMapper();

    AIAnalysisCacheProperties properties = redisProperties();

    when(redis.value(String.class)).thenReturn(ops);

    RedisAIAnalysisCache cache =
        new RedisAIAnalysisCache(redis, mapper, properties, mock(OperationsMetrics.class));

    SearchAnalysis analysis = SearchAnalysis.fallback("desk");

    cache.put(new CacheKey("key"), analysis);

    verify(ops).set(eq("test:analysis:key"), anyString(), any(SetArgs.class));
  }

  @Test
  void shouldIgnoreWriteFailures() {
    RedisDataSource redis = mock(RedisDataSource.class);
    ValueCommands<String, String> ops = mock(ValueCommands.class);
    ObjectMapper mapper = new ObjectMapper();

    AIAnalysisCacheProperties properties = redisProperties();

    when(redis.value(String.class)).thenReturn(ops);
    doThrow(new RuntimeException("redis down")).when(ops).set(anyString(), anyString(), any());

    RedisAIAnalysisCache cache =
        new RedisAIAnalysisCache(redis, mapper, properties, mock(OperationsMetrics.class));

    cache.put(new CacheKey("key"), SearchAnalysis.fallback("desk"));

    verify(ops).set(anyString(), anyString(), any(SetArgs.class));
  }

  @Test
  void shouldEvictConfiguredKey() {
    RedisDataSource redis = mock(RedisDataSource.class);
    ValueCommands<String, String> ops = mock(ValueCommands.class);
    KeyCommands<String> keys = mock(KeyCommands.class);

    when(redis.value(String.class)).thenReturn(ops);
    when(redis.key()).thenReturn(keys);

    RedisAIAnalysisCache cache =
        new RedisAIAnalysisCache(
            redis, new ObjectMapper(), redisProperties(), mock(OperationsMetrics.class));

    cache.evict(new CacheKey("key"));

    verify(keys).del("test:analysis:key");
  }

  private AIAnalysisCacheProperties redisProperties() {
    AIAnalysisCacheProperties properties = new AIAnalysisCacheProperties();
    properties.setEnabled(true);
    properties.setType("redis");
    properties.setKeyPrefix("test:analysis");
    return properties;
  }
}

package io.qwenbridge.analysis.cache.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.analysis.cache.CacheKey;
import io.qwenbridge.analysis.cache.config.AIAnalysisCacheProperties;
import io.qwenbridge.analysis.model.SearchAnalysis;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RedisAIAnalysisCacheTest {

    @Test
    void shouldReturnCachedAnalysisWhenPayloadExists() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        ObjectMapper mapper = new ObjectMapper();

        AIAnalysisCacheProperties properties = new AIAnalysisCacheProperties();
        properties.setKeyPrefix("test:analysis");

        SearchAnalysis analysis = SearchAnalysis.fallback("desk");
        String payload = mapper.writeValueAsString(analysis);

        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get("test:analysis:key")).thenReturn(payload);

        RedisAIAnalysisCache cache =
                new RedisAIAnalysisCache(redis, mapper, properties);

        assertThat(cache.get(new CacheKey("key")))
                .contains(analysis);
    }

    @Test
    void shouldReturnEmptyWhenRedisFails() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ObjectMapper mapper = new ObjectMapper();

        AIAnalysisCacheProperties properties = new AIAnalysisCacheProperties();
        properties.setKeyPrefix("test:analysis");

        when(redis.opsForValue()).thenThrow(new RuntimeException("redis down"));

        RedisAIAnalysisCache cache =
                new RedisAIAnalysisCache(redis, mapper, properties);

        assertThat(cache.get(new CacheKey("key"))).isEmpty();
    }

    @Test
    void shouldWritePayloadWithConfiguredTtl() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        ObjectMapper mapper = new ObjectMapper();

        AIAnalysisCacheProperties properties = new AIAnalysisCacheProperties();
        properties.setKeyPrefix("test:analysis");
        properties.setTtl(Duration.ofMinutes(5));

        when(redis.opsForValue()).thenReturn(ops);

        RedisAIAnalysisCache cache =
                new RedisAIAnalysisCache(redis, mapper, properties);

        SearchAnalysis analysis = SearchAnalysis.fallback("desk");

        cache.put(new CacheKey("key"), analysis);

        verify(ops).set(
                eq("test:analysis:key"),
                anyString(),
                eq(Duration.ofMinutes(5))
        );
    }

    @Test
    void shouldIgnoreWriteFailures() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        ObjectMapper mapper = new ObjectMapper();

        AIAnalysisCacheProperties properties = new AIAnalysisCacheProperties();
        properties.setKeyPrefix("test:analysis");

        when(redis.opsForValue()).thenReturn(ops);
        doThrow(new RuntimeException("redis down"))
                .when(ops)
                .set(anyString(), anyString(), any(Duration.class));

        RedisAIAnalysisCache cache =
                new RedisAIAnalysisCache(redis, mapper, properties);

        cache.put(new CacheKey("key"), SearchAnalysis.fallback("desk"));

        verify(ops).set(anyString(), anyString(), any(Duration.class));
    }
}

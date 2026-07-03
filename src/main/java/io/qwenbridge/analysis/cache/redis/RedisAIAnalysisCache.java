package io.qwenbridge.analysis.cache.redis;

import lombok.RequiredArgsConstructor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.analysis.cache.AIAnalysisCache;
import io.qwenbridge.analysis.cache.CacheKey;
import io.qwenbridge.analysis.cache.config.AIAnalysisCacheProperties;
import io.qwenbridge.analysis.model.SearchAnalysis;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${qwenbridge.analysis.cache.enabled:true}' == 'true' && '${qwenbridge.analysis.cache.type:redis}' == 'redis'")
public class RedisAIAnalysisCache implements AIAnalysisCache {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AIAnalysisCacheProperties properties;

    @Override
    public Optional<SearchAnalysis> get(CacheKey key) {
        if (!properties.enabled() || !isRedis()) {
            return Optional.empty();
        }

        try {
            String payload = redisTemplate.opsForValue().get(redisKey(key));

            if (payload == null || payload.isBlank()) {
                return Optional.empty();
            }

            return Optional.of(objectMapper.readValue(payload, SearchAnalysis.class));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @Override
    public void put(CacheKey key, SearchAnalysis value) {
        if (!properties.enabled() || !isRedis() || value == null) {
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(redisKey(key), payload, properties.ttl());
        } catch (Exception ignored) {
            // Redis/cache failures must never break the AI pipeline.
        }
    }

    @Override
    public void evict(CacheKey key) {
        if (!properties.enabled() || !isRedis()) {
            return;
        }

        try {
            redisTemplate.delete(redisKey(key));
        } catch (Exception ignored) {
            // Redis/cache failures must never break the AI pipeline.
        }
    }

    @Override
    public void clear() {
        // Intentionally no global Redis flush.
    }

    private boolean isRedis() {
        return "redis".equalsIgnoreCase(properties.type());
    }

    private String redisKey(CacheKey key) {
        String value = key == null ? "" : key.value();
        return properties.keyPrefix() + ":" + value;
    }
}

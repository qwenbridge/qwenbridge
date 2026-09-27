package io.qwenbridge.analysis.cache.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.analysis.cache.AIAnalysisCache;
import io.qwenbridge.analysis.cache.CacheKey;
import io.qwenbridge.analysis.cache.config.AIAnalysisCacheProperties;
import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.operations.metrics.OperationsMetrics;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class RedisAIAnalysisCache implements AIAnalysisCache {

  private final RedisDataSource redis;
  private final ValueCommands<String, String> values;
  private final ObjectMapper objectMapper;
  private final AIAnalysisCacheProperties properties;
  private final OperationsMetrics metrics;

  public RedisAIAnalysisCache(
      RedisDataSource redis,
      ObjectMapper objectMapper,
      AIAnalysisCacheProperties properties,
      OperationsMetrics metrics) {
    this.redis = redis;
    this.values = redis.value(String.class);
    this.objectMapper = objectMapper;
    this.properties = properties;
    this.metrics = metrics;
  }

  @Override
  public Optional<SearchAnalysis> get(CacheKey key) {
    if (!properties.enabled() || !isRedis()) {
      return Optional.empty();
    }

    try {
      String payload = values.get(redisKey(key));

      if (payload == null || payload.isBlank()) {
        record("miss");
        return Optional.empty();
      }

      record("hit");
      return Optional.of(objectMapper.readValue(payload, SearchAnalysis.class));
    } catch (Exception ignored) {
      record("fallback");
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
      values.set(redisKey(key), payload, new SetArgs().px(properties.ttl()));
      record("put");
    } catch (Exception ignored) {
      record("fallback");
      // Redis/cache failures must never break the AI pipeline.
    }
  }

  @Override
  public void evict(CacheKey key) {
    if (!properties.enabled() || !isRedis()) {
      return;
    }

    try {
      redis.key().del(redisKey(key));
      record("evict");
    } catch (Exception ignored) {
      record("fallback");
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

  private void record(String result) {
    metrics.incrementCache("analysis", result);
  }

  private String redisKey(CacheKey key) {
    String value = key == null ? "" : key.value();
    return properties.keyPrefix() + ":" + value;
  }
}

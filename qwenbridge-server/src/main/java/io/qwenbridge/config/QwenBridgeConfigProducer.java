package io.qwenbridge.config;

import io.qwenbridge.abuse.AbuseProtectionProperties;
import io.qwenbridge.ai.config.AIProperties;
import io.qwenbridge.ai.provider.ollama.config.OllamaProperties;
import io.qwenbridge.analysis.cache.config.AIAnalysisCacheProperties;
import io.qwenbridge.execution.provider.opensearch.OpenSearchProperties;
import io.qwenbridge.streaming.config.StreamingProperties;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.net.URI;
import java.time.Duration;
import org.eclipse.microprofile.config.Config;

/**
 * Builds QwenBridge configuration property objects from MicroProfile Config. Replaces the former
 * Spring {@code @ConfigurationProperties} binding while preserving the plain property record/POJO
 * types so they remain directly constructable in tests.
 */
@ApplicationScoped
public class QwenBridgeConfigProducer {

  private final Config config;

  @Inject
  public QwenBridgeConfigProducer(Config config) {
    this.config = config;
  }

  @Produces
  @Singleton
  public AIProperties aiProperties() {
    return new AIProperties(str("qwenbridge.ai.provider", "ollama"));
  }

  @Produces
  @Singleton
  public OllamaProperties ollamaProperties() {
    return new OllamaProperties(
        opt("qwenbridge.ai.ollama.base-url", URI.class, null),
        str("qwenbridge.ai.ollama.chat-model", null),
        str("qwenbridge.ai.ollama.embedding-model", null),
        opt("qwenbridge.ai.ollama.connect-timeout", Duration.class, null),
        opt("qwenbridge.ai.ollama.read-timeout", Duration.class, null),
        integer("qwenbridge.ai.ollama.retry-count", 2),
        bool("qwenbridge.ai.ollama.streaming-enabled", true));
  }

  @Produces
  @Singleton
  public OpenSearchProperties openSearchProperties() {
    return new OpenSearchProperties(
        str("qwenbridge.search.opensearch.base-url", null),
        str("qwenbridge.search.opensearch.index", null),
        integer("qwenbridge.search.opensearch.default-size", 0),
        opt("qwenbridge.search.opensearch.connect-timeout", Duration.class, null),
        opt("qwenbridge.search.opensearch.read-timeout", Duration.class, null));
  }

  @Produces
  @Singleton
  public StreamingProperties streamingProperties() {
    return new StreamingProperties(
        longValue("qwenbridge.streaming.session-timeout-ms", 0L),
        opt("qwenbridge.streaming.max-ai-stream-duration", Duration.class, null),
        longValue("qwenbridge.streaming.max-ai-token-count", 0L),
        longValue("qwenbridge.streaming.max-ai-event-count", 0L));
  }

  @Produces
  @Singleton
  public AbuseProtectionProperties abuseProtectionProperties() {
    return new AbuseProtectionProperties(
        bool("qwenbridge.abuse.enabled", true),
        integer("qwenbridge.abuse.request-size-limit-bytes", 0),
        integer("qwenbridge.abuse.per-ip-limit", 0),
        integer("qwenbridge.abuse.per-api-key-limit", 0),
        integer("qwenbridge.abuse.ai-request-quota", 0),
        integer("qwenbridge.abuse.token-quota", 0),
        integer("qwenbridge.abuse.concurrent-stream-limit", 0),
        opt("qwenbridge.abuse.window", Duration.class, null),
        opt("qwenbridge.abuse.redis-timeout", Duration.class, null),
        bool("qwenbridge.abuse.fail-open-when-redis-unavailable", false));
  }

  @Produces
  @Singleton
  public AIAnalysisCacheProperties aiAnalysisCacheProperties() {
    AIAnalysisCacheProperties properties = new AIAnalysisCacheProperties();
    properties.setEnabled(bool("qwenbridge.analysis.cache.enabled", true));
    properties.setType(str("qwenbridge.analysis.cache.type", "redis"));
    properties.setKeyPrefix(str("qwenbridge.analysis.cache.key-prefix", "qwenbridge:analysis"));
    properties.setProvider(str("qwenbridge.analysis.cache.provider", "ollama"));
    properties.setModel(str("qwenbridge.analysis.cache.model", "qwen2.5"));
    properties.setVersion(str("qwenbridge.analysis.cache.version", "v4"));
    properties.setAnalysisTimeout(
        opt("qwenbridge.analysis.cache.analysis-timeout", Duration.class, Duration.ofSeconds(10)));
    properties.setTtl(opt("qwenbridge.analysis.cache.ttl", Duration.class, Duration.ofMinutes(10)));

    AIAnalysisCacheProperties.Redis redis = new AIAnalysisCacheProperties.Redis();
    redis.setHost(str("qwenbridge.analysis.cache.redis.host", "localhost"));
    redis.setPort(integer("qwenbridge.analysis.cache.redis.port", 6379));
    redis.setConnectTimeout(
        opt("qwenbridge.analysis.cache.redis.connect-timeout", Duration.class, Duration.ofSeconds(2)));
    redis.setCommandTimeout(
        opt("qwenbridge.analysis.cache.redis.command-timeout", Duration.class, Duration.ofSeconds(2)));
    properties.setRedis(redis);

    return properties;
  }

  private <T> T opt(String key, Class<T> type, T defaultValue) {
    return config.getOptionalValue(key, type).orElse(defaultValue);
  }

  private String str(String key, String defaultValue) {
    return config.getOptionalValue(key, String.class).orElse(defaultValue);
  }

  private int integer(String key, int defaultValue) {
    return config.getOptionalValue(key, Integer.class).orElse(defaultValue);
  }

  private long longValue(String key, long defaultValue) {
    return config.getOptionalValue(key, Long.class).orElse(defaultValue);
  }

  private boolean bool(String key, boolean defaultValue) {
    return config.getOptionalValue(key, Boolean.class).orElse(defaultValue);
  }
}

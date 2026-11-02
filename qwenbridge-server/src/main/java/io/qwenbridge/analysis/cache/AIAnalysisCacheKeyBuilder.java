package io.qwenbridge.analysis.cache;

import io.qwenbridge.analysis.cache.config.AIAnalysisCacheProperties;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class AIAnalysisCacheKeyBuilder {

  private final AIAnalysisCacheProperties properties;
  private final CacheKeyBuilder delegate;

  public CacheKey build(String normalizedQuery) {
    return delegate.build(
        normalizedQuery, properties.provider(), properties.model(), properties.version());
  }
}

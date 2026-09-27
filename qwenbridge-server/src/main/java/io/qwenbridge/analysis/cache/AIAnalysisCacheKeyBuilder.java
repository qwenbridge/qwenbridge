package io.qwenbridge.analysis.cache;

import io.qwenbridge.analysis.cache.config.AIAnalysisCacheProperties;
import lombok.RequiredArgsConstructor;
import jakarta.enterprise.context.ApplicationScoped;

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

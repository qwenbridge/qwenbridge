package io.qwenbridge.analysis.cache;

import io.qwenbridge.analysis.model.SearchAnalysis;
import io.quarkus.arc.DefaultBean;
import java.util.Optional;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@DefaultBean
public class NoOpAIAnalysisCache implements AIAnalysisCache {

  @Override
  public Optional<SearchAnalysis> get(CacheKey key) {
    return Optional.empty();
  }

  @Override
  public void put(CacheKey key, SearchAnalysis value) {}

  @Override
  public void evict(CacheKey key) {}

  @Override
  public void clear() {}
}

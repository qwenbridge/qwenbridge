package io.qwenbridge.analysis.cache;

import io.qwenbridge.analysis.model.SearchAnalysis;
import java.util.Optional;

public interface AIAnalysisCache {

  Optional<SearchAnalysis> get(CacheKey key);

  void put(CacheKey key, SearchAnalysis value);

  void evict(CacheKey key);

  void clear();
}

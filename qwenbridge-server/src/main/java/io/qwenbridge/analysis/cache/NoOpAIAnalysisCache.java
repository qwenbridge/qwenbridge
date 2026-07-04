package io.qwenbridge.analysis.cache;

import io.qwenbridge.analysis.model.SearchAnalysis;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnMissingBean(AIAnalysisCache.class)
public class NoOpAIAnalysisCache implements AIAnalysisCache {

    @Override
    public Optional<SearchAnalysis> get(CacheKey key) {
        return Optional.empty();
    }

    @Override
    public void put(CacheKey key, SearchAnalysis value) {
    }

    @Override
    public void evict(CacheKey key) {
    }

    @Override
    public void clear() {
    }
}

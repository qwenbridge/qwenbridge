package io.qwenbridge.analysis.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpAIAnalysisCacheTest {

    private final NoOpAIAnalysisCache cache = new NoOpAIAnalysisCache();

    @Test
    void shouldAlwaysMiss() {
        assertThat(cache.get(new CacheKey("key"))).isEmpty();
    }

    @Test
    void shouldIgnoreWritesAndEvictions() {
        CacheKey key = new CacheKey("key");

        cache.put(key, null);
        cache.evict(key);
        cache.clear();

        assertThat(cache.get(key)).isEmpty();
    }
}

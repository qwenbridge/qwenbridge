package io.qwenbridge.analysis.cache;

import io.qwenbridge.analysis.cache.config.AIAnalysisCacheProperties;
import org.springframework.stereotype.Component;

@Component
public class AIAnalysisCacheKeyBuilder {

    private final AIAnalysisCacheProperties properties;
    private final CacheKeyBuilder delegate;

    public AIAnalysisCacheKeyBuilder(
            AIAnalysisCacheProperties properties,
            CacheKeyBuilder delegate
    ) {
        this.properties = properties;
        this.delegate = delegate;
    }

    public CacheKey build(String normalizedQuery) {
        return delegate.build(
                normalizedQuery,
                properties.provider(),
                properties.model(),
                properties.version()
        );
    }
}

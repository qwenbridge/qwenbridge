package io.qwenbridge.analysis.cache;

public interface CacheKeyBuilder {

    CacheKey build(
            String normalizedQuery,
            String provider,
            String model,
            String version
    );
}

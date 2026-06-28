package io.qwenbridge.analysis.cache.trace;

public record CacheTrace(
        boolean hit,
        String key
) {
}

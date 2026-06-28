package io.qwenbridge.analysis.cache;

import io.qwenbridge.analysis.model.SearchAnalysis;

import java.time.Instant;

public record CacheEntry(
        SearchAnalysis value,
        Instant cachedAt
) {
    public CacheEntry {
        cachedAt = cachedAt == null ? Instant.now() : cachedAt;
    }
}

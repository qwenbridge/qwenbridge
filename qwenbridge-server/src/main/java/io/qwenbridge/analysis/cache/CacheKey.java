package io.qwenbridge.analysis.cache;

public record CacheKey(String value) {

    public CacheKey {
        value = value == null ? "" : value;
    }
}

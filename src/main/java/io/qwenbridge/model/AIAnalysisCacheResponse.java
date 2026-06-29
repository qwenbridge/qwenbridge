package io.qwenbridge.model;

import lombok.Builder;

@Builder
public record AIAnalysisCacheResponse(
        boolean enabled,
        boolean hit,
        boolean miss,
        String key,
        String provider,
        String model,
        String version
) {
    public AIAnalysisCacheResponse {
        key = key == null ? "" : key;
        provider = provider == null ? "" : provider;
        model = model == null ? "" : model;
        version = version == null ? "" : version;
    }
}

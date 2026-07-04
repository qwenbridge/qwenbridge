package io.qwenbridge.model;

import lombok.Builder;

import java.util.Map;
import java.util.Objects;

@Builder
public record SearchHitResponse(
        String id,
        double score,
        Map<String, Object> document
) {
    public SearchHitResponse {
        Objects.requireNonNull(id, "id must not be null");
        document = Map.copyOf(Objects.requireNonNull(document, "document must not be null"));
    }
}
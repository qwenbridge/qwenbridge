package io.qwenbridge.model;

import java.util.Map;
import java.util.Objects;

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
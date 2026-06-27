package io.qwenbridge.execution.provider.model;

import java.util.Map;
import java.util.Objects;

public record SearchRequest(
        String query,
        Map<String, Object> constraints,
        Map<String, Object> options
) {

    public SearchRequest {
        Objects.requireNonNull(query, "query must not be null");
        constraints = Map.copyOf(Objects.requireNonNull(constraints, "constraints must not be null"));
        options = Map.copyOf(Objects.requireNonNull(options, "options must not be null"));
    }

    public static SearchRequest of(String query) {
        return new SearchRequest(query, Map.of(), Map.of());
    }
}
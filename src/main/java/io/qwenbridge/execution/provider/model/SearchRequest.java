package io.qwenbridge.execution.provider.model;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record SearchRequest(
        String query,
        Map<String, Object> constraints,
        Map<String, Object> options
) {

    public static final String OPTION_SEARCH_MODE = "searchMode";
    public static final String OPTION_EMBEDDING = "embedding";

    public SearchRequest {
        Objects.requireNonNull(query, "query must not be null");
        constraints = Map.copyOf(Objects.requireNonNull(constraints, "constraints must not be null"));
        options = Map.copyOf(Objects.requireNonNull(options, "options must not be null"));
    }

    public static SearchRequest of(String query) {
        return new SearchRequest(query, Map.of(), Map.of());
    }

    public static SearchRequest keyword(String query) {
        return of(query);
    }

    public static SearchRequest vector(String query, List<Double> embedding) {
        return withModeAndEmbedding(query, "VECTOR", embedding);
    }

    public static SearchRequest hybrid(String query, List<Double> embedding) {
        return withModeAndEmbedding(query, "HYBRID", embedding);
    }

    public static SearchRequest withOptions(
            String query,
            Map<String, Object> options
    ) {
        return new SearchRequest(query, Map.of(), options);
    }

    public String searchMode() {
        Object value = options.get(OPTION_SEARCH_MODE);

        if (value == null || value.toString().isBlank()) {
            return "KEYWORD";
        }

        return value.toString().trim().toUpperCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    public Optional<List<Double>> embedding() {
        Object value = options.get(OPTION_EMBEDDING);

        if (!(value instanceof List<?> rawValues)) {
            return Optional.empty();
        }

        List<Double> vector = rawValues.stream()
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::doubleValue)
                .toList();

        if (vector.size() != rawValues.size() || vector.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(vector);
    }

    private static SearchRequest withModeAndEmbedding(
            String query,
            String mode,
            List<Double> embedding
    ) {
        return withOptions(
                query,
                Map.of(
                        OPTION_SEARCH_MODE, mode,
                        OPTION_EMBEDDING, List.copyOf(embedding)
                )
        );
    }
}

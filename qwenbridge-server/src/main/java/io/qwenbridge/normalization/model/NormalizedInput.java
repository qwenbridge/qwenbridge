package io.qwenbridge.normalization.model;

import java.util.List;

public record NormalizedInput(
        String originalQuery,
        String normalizedQuery,
        List<NormalizationTraceItem> trace
) {
    public NormalizedInput {
        originalQuery = safe(originalQuery);
        normalizedQuery = safe(normalizedQuery);
        trace = trace == null ? List.of() : List.copyOf(trace);
    }

    public static NormalizedInput unchanged(String query) {
        String safeQuery = safe(query);
        return new NormalizedInput(safeQuery, safeQuery, List.of());
    }

    public boolean changed() {
        return !originalQuery.equals(normalizedQuery);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

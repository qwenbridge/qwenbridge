package io.qwenbridge.model;

import java.util.List;
import java.util.Objects;

public record SearchResultResponse(
        boolean available,
        long totalHits,
        long tookMillis,
        List<SearchHitResponse> hits
) {
    public SearchResultResponse {
        hits = List.copyOf(Objects.requireNonNull(hits, "hits must not be null"));
    }

    public static SearchResultResponse unavailable() {
        return new SearchResultResponse(false, 0, 0, List.of());
    }
}
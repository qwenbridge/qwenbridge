package io.qwenbridge.execution.provider.model;

import java.util.List;
import java.util.Objects;

public record SearchResultSet(
        List<SearchHit> hits,
        long totalHits,
        long tookMillis
) {

    public SearchResultSet {
        hits = List.copyOf(Objects.requireNonNull(hits, "hits must not be null"));
    }

    public boolean isEmpty() {
        return hits.isEmpty();
    }

    public static SearchResultSet empty() {
        return new SearchResultSet(List.of(), 0, 0);
    }
}
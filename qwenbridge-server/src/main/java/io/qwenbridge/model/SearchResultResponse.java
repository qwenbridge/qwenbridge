package io.qwenbridge.model;

import java.util.List;
import java.util.Objects;
import lombok.Builder;

@Builder
public record SearchResultResponse(
    boolean available, long totalHits, long tookMillis, List<SearchHitResponse> hits) {
  public SearchResultResponse {
    hits = List.copyOf(Objects.requireNonNull(hits, "hits must not be null"));
  }

  public static SearchResultResponse unavailable() {
    return SearchResultResponse.builder()
        .available(false)
        .totalHits(0)
        .tookMillis(0)
        .hits(List.of())
        .build();
  }
}

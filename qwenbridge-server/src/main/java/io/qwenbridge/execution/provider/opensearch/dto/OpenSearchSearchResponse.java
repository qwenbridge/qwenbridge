package io.qwenbridge.execution.provider.opensearch.dto;

import java.util.List;

public record OpenSearchSearchResponse(List<OpenSearchHit> hits, long totalHits, long tookMillis) {
  public static OpenSearchSearchResponse empty() {
    return new OpenSearchSearchResponse(List.of(), 0, 0);
  }
}

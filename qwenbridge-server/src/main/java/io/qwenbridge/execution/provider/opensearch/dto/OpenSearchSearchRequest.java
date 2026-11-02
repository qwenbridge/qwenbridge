package io.qwenbridge.execution.provider.opensearch.dto;

import java.util.Map;

public record OpenSearchSearchRequest(Map<String, Object> query, int size) {
  public OpenSearchSearchRequest {
    query = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(query));
  }
}

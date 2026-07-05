package io.qwenbridge.execution.provider.model;

import java.util.Objects;

public record SearchResponse(SearchResultSet results) {

  public SearchResponse {
    Objects.requireNonNull(results, "results must not be null");
  }

  public static SearchResponse empty() {
    return new SearchResponse(SearchResultSet.empty());
  }
}

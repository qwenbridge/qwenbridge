package io.qwenbridge.sdk.search;

public record SearchAnalyzeRequest(String requestId, String query) {

  public SearchAnalyzeRequest {
    if (query == null || query.isBlank()) {
      throw new IllegalArgumentException("query must not be blank");
    }
  }

  public static SearchAnalyzeRequest of(String query) {
    return new SearchAnalyzeRequest(null, query);
  }

  public static SearchAnalyzeRequest withRequestId(String requestId, String query) {
    return new SearchAnalyzeRequest(requestId, query);
  }
}

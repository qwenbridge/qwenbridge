package io.qwenbridge.sdk.search;

public record SearchAnalyzeRequest(
    String requestId, String query, String declaredLanguage, String locale) {

  public SearchAnalyzeRequest {
    if (query == null || query.isBlank()) {
      throw new IllegalArgumentException("query must not be blank");
    }

    if (declaredLanguage != null && !declaredLanguage.matches("^[A-Za-z]{2}$")) {
      throw new IllegalArgumentException("declaredLanguage must be a two-letter language code");
    }

    if (locale != null && !locale.matches("^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*$")) {
      throw new IllegalArgumentException("locale must be a supported BCP 47 language tag");
    }
  }

  public SearchAnalyzeRequest(String requestId, String query) {
    this(requestId, query, null, null);
  }

  public static SearchAnalyzeRequest of(String query) {
    return new SearchAnalyzeRequest(null, query, null, null);
  }

  public static SearchAnalyzeRequest withRequestId(String requestId, String query) {
    return new SearchAnalyzeRequest(requestId, query, null, null);
  }

  public static SearchAnalyzeRequest multilingual(
      String query, String declaredLanguage, String locale) {
    return new SearchAnalyzeRequest(null, query, declaredLanguage, locale);
  }

  public static SearchAnalyzeRequest multilingual(
      String requestId, String query, String declaredLanguage, String locale) {
    return new SearchAnalyzeRequest(requestId, query, declaredLanguage, locale);
  }
}

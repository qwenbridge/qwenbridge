package io.qwenbridge.decision;

import java.util.Objects;

public record SearchDecision(
    SearchMode mode,
    SearchBackend backend,
    boolean keywordSearch,
    boolean vectorSearch,
    boolean hybridSearch,
    boolean facets,
    boolean rerank,
    boolean rewriteAgain,
    boolean answer,
    double confidence,
    String reason) {
  public SearchDecision {
    Objects.requireNonNull(mode, "mode must not be null");
    Objects.requireNonNull(backend, "backend must not be null");

    if (confidence < 0.0 || confidence > 1.0) {
      throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
    }

    if (reason == null || reason.isBlank()) {
      reason = "No decision reason provided.";
    }
  }

  public static SearchDecision keyword() {
    return new SearchDecision(
        SearchMode.KEYWORD,
        SearchBackend.IN_MEMORY,
        true,
        false,
        false,
        true,
        false,
        false,
        false,
        0.70,
        "Default keyword search decision.");
  }

  public static SearchDecision hybrid() {
    return new SearchDecision(
        SearchMode.HYBRID,
        SearchBackend.IN_MEMORY,
        true,
        true,
        true,
        true,
        true,
        false,
        false,
        0.85,
        "Hybrid search decision using keyword and vector signals.");
  }

  public static SearchDecision directAnswer() {
    return new SearchDecision(
        SearchMode.DIRECT_ANSWER,
        SearchBackend.NONE,
        false,
        false,
        false,
        false,
        false,
        false,
        true,
        0.80,
        "Direct answer decision without search backend execution.");
  }
}

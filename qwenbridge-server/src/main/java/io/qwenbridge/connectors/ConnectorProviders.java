package io.qwenbridge.connectors;

import io.qwenbridge.core.confidence.RetrievalScore;
import io.qwenbridge.core.retrieval.CancellationToken;
import io.qwenbridge.core.retrieval.ProviderCapabilities;
import io.qwenbridge.core.retrieval.RetrievalMode;
import io.qwenbridge.core.retrieval.ScoredDocument;
import io.qwenbridge.core.retrieval.SearchOutcome;
import io.qwenbridge.core.retrieval.SearchRequest;
import io.qwenbridge.core.retrieval.filter.FilterFeature;
import io.qwenbridge.execution.provider.model.SearchHit;
import io.qwenbridge.execution.provider.model.SearchResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Shared behaviour of connectors implementing the unified search SPI (QB-107): capability
 * declaration, checking a request before anything is sent, and mapping results and exceptions to
 * typed outcomes. Connector scores are backend-native and only comparable within one call.
 */
final class ConnectorProviders {

  static final int MAX_TOP_K = 100;

  private ConnectorProviders() {}

  static ProviderCapabilities keywordCapabilities() {
    return new ProviderCapabilities(
        EnumSet.of(RetrievalMode.KEYWORD),
        EnumSet.allOf(FilterFeature.class),
        Optional.empty(),
        MAX_TOP_K,
        false);
  }

  /**
   * The unified-SPI keyword query for OpenSearch: BM25 over the text fields (match-all for a
   * structured-only query), the neutral filter with the tenant term, ordered by score then document
   * id so pages are stable.
   */
  static java.util.Map<String, Object> keywordPayload(SearchRequest request) {
    String text = request.text().orElse("");
    java.util.Map<String, Object> keyword =
        text.isBlank()
            ? java.util.Map.of("match_all", java.util.Map.of())
            : java.util.Map.of(
                "multi_match",
                java.util.Map.of(
                    "query",
                    text,
                    "fields",
                    List.of("title^4", "brand^2", "category", "description")));
    java.util.Map<String, Object> payload = new java.util.HashMap<>();
    payload.put("size", request.topK());
    payload.put("track_total_hits", true);
    payload.put("_source", java.util.Map.of("excludes", List.of("embedding")));
    payload.put(
        "sort",
        List.of(
            java.util.Map.of("_score", "desc"),
            java.util.Map.of("documentId", java.util.Map.of("order", "asc"))));
    payload.put(
        "query",
        java.util.Map.of(
            "bool",
            java.util.Map.of(
                "must",
                List.of(keyword),
                "filter",
                List.of(NeutralFilterTranslator.openSearch(request.tenant(), request.filter())))));
    return payload;
  }

  static SearchOutcome run(
      String id,
      boolean configured,
      ProviderCapabilities capabilities,
      SearchRequest request,
      CancellationToken cancellation,
      Supplier<SearchResultSet> call) {
    Optional<String> unmet = capabilities.unmet(request);
    if (unmet.isPresent()) {
      return new SearchOutcome.Unsupported(unmet.get());
    }
    if (!configured) {
      return new SearchOutcome.Failed(SearchOutcome.Failure.UNAVAILABLE, id + " is not configured");
    }
    if (cancellation.cancelled()) {
      return new SearchOutcome.Failed(SearchOutcome.Failure.CANCELLED, id);
    }
    long started = System.nanoTime();
    SearchResultSet results;
    try {
      results = call.get();
    } catch (RuntimeException e) {
      boolean timeout =
          e.getMessage() != null
              && e.getMessage().toLowerCase(java.util.Locale.ROOT).contains("timed out");
      return new SearchOutcome.Failed(
          timeout ? SearchOutcome.Failure.TIMEOUT : SearchOutcome.Failure.UNAVAILABLE, id);
    }
    List<ScoredDocument> hits = new ArrayList<>();
    for (SearchHit hit : results.hits()) {
      // Stored documents carry their own id; backend-internal ids (tenant-scoped digests) never
      // leave the connector.
      Object documentId = hit.document().get("documentId");
      hits.add(
          new ScoredDocument(
              documentId == null ? hit.id() : documentId.toString(),
              hits.size() + 1,
              new RetrievalScore(hit.score(), RetrievalScore.Kind.BACKEND_NATIVE, id),
              NeutralFilterTranslator.fields(hit.document())));
    }
    return new SearchOutcome.Success(
        hits, results.totalHits(), Duration.ofNanos(System.nanoTime() - started));
  }
}

package io.qwenbridge.execution.provider.opensearch.query;

import io.qwenbridge.execution.provider.model.SearchRequest;
import io.qwenbridge.execution.provider.opensearch.OpenSearchProperties;
import io.qwenbridge.execution.provider.opensearch.dto.OpenSearchSearchRequest;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OpenSearchQueryFactory {

  private static final String EMBEDDING_FIELD = "embedding";

  private final OpenSearchProperties properties;

  public OpenSearchSearchRequest from(SearchRequest request) {
    return switch (request.searchMode()) {
      case "VECTOR", "SEMANTIC" -> vectorSearch(request);
      case "HYBRID" -> hybridSearch(request);
      default -> keywordSearch(request);
    };
  }

  private OpenSearchSearchRequest keywordSearch(SearchRequest request) {
    return new OpenSearchSearchRequest(keywordQuery(request.query()), properties.defaultSize());
  }

  private OpenSearchSearchRequest vectorSearch(SearchRequest request) {
    return new OpenSearchSearchRequest(
        request.embedding().map(this::vectorQuery).orElseGet(() -> keywordQuery(request.query())),
        properties.defaultSize());
  }

  private OpenSearchSearchRequest hybridSearch(SearchRequest request) {
    return new OpenSearchSearchRequest(
        request
            .embedding()
            .map(
                embedding ->
                    Map.<String, Object>of(
                        "bool",
                        Map.of(
                            "should",
                            List.of(keywordQuery(request.query()), vectorQuery(embedding)),
                            "minimum_should_match",
                            1)))
            .orElseGet(() -> keywordQuery(request.query())),
        properties.defaultSize());
  }

  private Map<String, Object> keywordQuery(String query) {
    return Map.of(
        "multi_match",
        Map.of("query", query, "fields", List.of("title^3", "brand^2", "category", "description")));
  }

  private Map<String, Object> vectorQuery(List<Double> embedding) {
    return Map.of(
        "knn", Map.of(EMBEDDING_FIELD, Map.of("vector", embedding, "k", properties.defaultSize())));
  }
}

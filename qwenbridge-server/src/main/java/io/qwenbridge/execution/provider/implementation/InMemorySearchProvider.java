package io.qwenbridge.execution.provider.implementation;

import io.qwenbridge.execution.provider.model.SearchHit;
import io.qwenbridge.execution.provider.model.SearchRequest;
import io.qwenbridge.execution.provider.model.SearchResponse;
import io.qwenbridge.execution.provider.model.SearchResultSet;
import io.qwenbridge.execution.provider.support.AbstractSearchProvider;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class InMemorySearchProvider extends AbstractSearchProvider {

  private final List<Map<String, Object>> documents =
      List.of(
          Map.of(
              "id", "product-1",
              "title", "iPhone 16 Pro",
              "brand", "Apple",
              "category", "smartphone"),
          Map.of(
              "id", "product-2",
              "title", "Samsung Galaxy S25",
              "brand", "Samsung",
              "category", "smartphone"),
          Map.of(
              "id", "product-3",
              "title", "Sony WH-1000XM5",
              "brand", "Sony",
              "category", "headphones"));

  public InMemorySearchProvider() {
    super("inmemory");
  }

  @Override
  protected SearchResponse doSearch(SearchRequest request) {
    String query = request.query().toLowerCase(Locale.ROOT);

    List<SearchHit> hits =
        documents.stream()
            .filter(document -> matches(document, query))
            .map(document -> SearchHit.of(document.get("id").toString(), 1.0, document))
            .toList();

    return new SearchResponse(new SearchResultSet(hits, hits.size(), 0));
  }

  private boolean matches(Map<String, Object> document, String query) {
    return document.values().stream()
        .map(Object::toString)
        .map(value -> value.toLowerCase(Locale.ROOT))
        .anyMatch(value -> value.contains(query));
  }
}

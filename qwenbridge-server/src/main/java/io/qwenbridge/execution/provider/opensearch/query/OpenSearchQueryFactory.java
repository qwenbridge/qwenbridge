package io.qwenbridge.execution.provider.opensearch.query;

import io.qwenbridge.execution.provider.model.SearchRequest;
import io.qwenbridge.execution.provider.opensearch.OpenSearchProperties;
import io.qwenbridge.execution.provider.opensearch.dto.OpenSearchSearchRequest;
import io.qwenbridge.intelligence.ir.QueryOperator;
import io.qwenbridge.intelligence.planning.LogicalQueryPlan;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class OpenSearchQueryFactory {

  private static final String EMBEDDING_FIELD = "embedding";

  private final OpenSearchProperties properties;

  public OpenSearchSearchRequest from(SearchRequest request) {
    if (request.constraints().size() == 1
        && request.constraints().get("commercePlan")
            instanceof io.qwenbridge.commerce.CommerceContracts.Plan plan) {
      return commerce(request, plan);
    }
    if (!request.constraints().isEmpty()) {
      throw new UnsupportedOperationException(
          "Constraints require validated structured compilation");
    }
    return switch (request.searchMode()) {
      case "VECTOR", "SEMANTIC" -> vectorSearch(request);
      case "HYBRID" -> hybridSearch(request);
      default -> keywordSearch(request);
    };
  }

  private OpenSearchSearchRequest commerce(
      SearchRequest request, io.qwenbridge.commerce.CommerceContracts.Plan plan) {
    var filters = io.qwenbridge.commerce.CommerceCompiler.filters(plan);
    Map<String, Object> query;
    if ("KEYWORD".equals(request.searchMode())) {
      query = keywordQuery(request.query());
    } else {
      var embedding =
          request
              .embedding()
              .orElseThrow(() -> new IllegalArgumentException("Vector mode requires an embedding"));
      Map<String, Object> vector =
          Map.of(
              "knn",
              Map.of(
                  EMBEDDING_FIELD,
                  Map.of(
                      "vector",
                      embedding,
                      "k",
                      properties.defaultSize(),
                      "filter",
                      Map.of("bool", Map.of("filter", filters)))));
      query =
          switch (request.searchMode()) {
            case "VECTOR", "SEMANTIC" -> vector;
            case "HYBRID" ->
                Map.of(
                    "bool",
                    Map.of(
                        "should",
                        List.of(keywordQuery(request.query()), vector),
                        "minimum_should_match",
                        1));
            default -> throw new IllegalArgumentException("Unsupported commerce mode");
          };
    }
    return new OpenSearchSearchRequest(
        Map.of("bool", Map.of("must", List.of(query), "filter", filters)),
        properties.defaultSize());
  }

  /**
   * Opt-in structured filter compilation. Field names must come from trusted index mapping
   * configuration, never from user text. Existing from(request) behavior is unchanged.
   *
   * <p>Legacy ad-hoc constraints remain keyword-only; typed commerce plans support all three modes.
   */
  public OpenSearchSearchRequest from(
      SearchRequest request, LogicalQueryPlan plan, String priceField, String currencyField) {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(plan, "plan");
    if (!request.query().equals(plan.originalQuery())) {
      throw new IllegalArgumentException("Search request and logical plan query differ");
    }
    if (plan.priceUpperBounds().isEmpty()) return from(request);
    if (!"KEYWORD".equals(request.searchMode())) {
      throw new UnsupportedOperationException("Structured filters require keyword mode");
    }
    if (!request.constraints().isEmpty()) {
      throw new UnsupportedOperationException("Unmapped request constraints cannot be ignored");
    }
    validateField(priceField);
    validateField(currencyField);
    if (priceField.equals(currencyField)) {
      throw new IllegalArgumentException("Price and currency fields must differ");
    }
    var currencies =
        plan.priceUpperBounds().stream()
            .map(LogicalQueryPlan.PriceUpperBound::currency)
            .distinct()
            .toList();
    if (currencies.size() != 1) {
      throw new UnsupportedOperationException("Multiple currencies require explicit conversion");
    }
    var filters = new java.util.ArrayList<Map<String, Object>>();
    filters.add(Map.of("term", Map.of(currencyField, currencies.get(0))));
    for (var bound : plan.priceUpperBounds()) {
      if (!"price".equals(bound.field())) {
        throw new UnsupportedOperationException("Unsupported logical field: " + bound.field());
      }
      String comparison = bound.operator() == QueryOperator.LESS_THAN ? "lt" : "lte";
      filters.add(Map.of("range", Map.of(priceField, Map.of(comparison, bound.amount()))));
    }
    return new OpenSearchSearchRequest(
        Map.of(
            "bool",
            Map.of("must", List.of(keywordQuery(request.query())), "filter", List.copyOf(filters))),
        properties.defaultSize());
  }

  private static void validateField(String field) {
    if (field == null || !field.matches("[A-Za-z_][A-Za-z0-9_.]*")) {
      throw new IllegalArgumentException("Invalid configured index field");
    }
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

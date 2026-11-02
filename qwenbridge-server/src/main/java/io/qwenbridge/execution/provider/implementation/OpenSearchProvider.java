package io.qwenbridge.execution.provider.implementation;

import io.qwenbridge.execution.provider.exception.SearchProviderException;
import io.qwenbridge.execution.provider.model.SearchRequest;
import io.qwenbridge.execution.provider.model.SearchResponse;
import io.qwenbridge.execution.provider.opensearch.OpenSearchProperties;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchClient;
import io.qwenbridge.execution.provider.opensearch.dto.OpenSearchSearchRequest;
import io.qwenbridge.execution.provider.opensearch.mapper.OpenSearchResponseMapper;
import io.qwenbridge.execution.provider.opensearch.query.OpenSearchQueryFactory;
import io.qwenbridge.execution.provider.support.AbstractSearchProvider;
import io.qwenbridge.intelligence.planning.LogicalQueryPlan;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.Objects;

@Singleton
public class OpenSearchProvider extends AbstractSearchProvider {

  @org.eclipse.microprofile.config.inject.ConfigProperty(
      name = "qwenbridge.search.opensearch.include-embeddings-in-results",
      defaultValue = "false")
  boolean includeEmbeddingsInResults;

  private final OpenSearchProperties properties;
  private final OpenSearchClient client;
  private final OpenSearchQueryFactory queryFactory;
  private final OpenSearchResponseMapper responseMapper;

  public OpenSearchProvider(
      OpenSearchProperties properties,
      OpenSearchClient client,
      OpenSearchQueryFactory queryFactory,
      OpenSearchResponseMapper responseMapper) {
    super("opensearch");
    this.properties = properties;
    this.client = client;
    this.queryFactory = queryFactory;
    this.responseMapper = responseMapper;
  }

  @Override
  protected SearchResponse doSearch(SearchRequest request) {
    return execute(queryFactory.from(request));
  }

  /** Opt-in execution of validated structured filters; legacy search remains unchanged. */
  public SearchResponse searchStructured(
      SearchRequest request, LogicalQueryPlan plan, String priceField, String currencyField) {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(plan, "plan");
    return execute(queryFactory.from(request, plan, priceField, currencyField));
  }

  private SearchResponse execute(OpenSearchSearchRequest searchRequest) {
    try {
      Map<String, Object> payload = new java.util.LinkedHashMap<>();
      payload.put("query", searchRequest.query());
      payload.put("size", searchRequest.size());
      if (!includeEmbeddingsInResults) {
        payload.put("_source", Map.of("excludes", java.util.List.of("embedding")));
      }
      Map<String, Object> rawResponse = client.search(properties.index(), payload);

      return responseMapper.from(rawResponse);
    } catch (RuntimeException ex) {
      throw new SearchProviderException("OpenSearch provider failure", ex);
    }
  }

  public OpenSearchProperties properties() {
    return properties;
  }
}

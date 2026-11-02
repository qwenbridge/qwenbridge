package io.qwenbridge.execution.provider.implementation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.qwenbridge.execution.provider.exception.SearchProviderException;
import io.qwenbridge.execution.provider.model.SearchRequest;
import io.qwenbridge.execution.provider.model.SearchResponse;
import io.qwenbridge.execution.provider.opensearch.OpenSearchProperties;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchClient;
import io.qwenbridge.execution.provider.opensearch.dto.OpenSearchSearchRequest;
import io.qwenbridge.execution.provider.opensearch.mapper.OpenSearchResponseMapper;
import io.qwenbridge.execution.provider.opensearch.query.OpenSearchQueryFactory;
import io.qwenbridge.intelligence.ir.QueryOperator;
import io.qwenbridge.intelligence.planning.LogicalQueryPlan;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenSearchStructuredProviderTest {
  private final OpenSearchProperties properties =
      new OpenSearchProperties(null, null, 10, null, null);
  private final OpenSearchClient client = mock(OpenSearchClient.class);
  private final OpenSearchQueryFactory factory = mock(OpenSearchQueryFactory.class);
  private final OpenSearchResponseMapper mapper = mock(OpenSearchResponseMapper.class);
  private final OpenSearchProvider provider =
      new OpenSearchProvider(properties, client, factory, mapper);
  private final SearchRequest request = SearchRequest.keyword("mouse under 100 SEK");
  private final LogicalQueryPlan plan =
      new LogicalQueryPlan(
          1,
          request.query(),
          List.of(),
          List.of(
              new LogicalQueryPlan.PriceUpperBound(
                  "price", QueryOperator.LESS_THAN, new BigDecimal("100"), "SEK")));

  @Test
  void executesCompiledStructuredQueryAndMapsResponse() {
    var compiled =
        new OpenSearchSearchRequest(
            Map.of(
                "bool",
                Map.of("filter", List.of(Map.of("range", Map.of("price", Map.of("lt", 100)))))),
            10);
    when(factory.from(request, plan, "price", "currency.keyword")).thenReturn(compiled);
    Map<String, Object> raw = Map.of("hits", Map.of("hits", List.of()));
    when(client.search(
            properties.index(),
            Map.of(
                "query",
                compiled.query(),
                "size",
                10,
                "_source",
                Map.of("excludes", List.of("embedding")))))
        .thenReturn(raw);
    SearchResponse response = SearchResponse.empty();
    when(mapper.from(raw)).thenReturn(response);

    assertThat(provider.searchStructured(request, plan, "price", "currency.keyword"))
        .isSameAs(response);
    verify(client)
        .search(
            properties.index(),
            Map.of(
                "query",
                compiled.query(),
                "size",
                10,
                "_source",
                Map.of("excludes", List.of("embedding"))));
    verify(factory, never()).from(request);
  }

  @Test
  void invalidStructuredQueryDoesNotReachBackend() {
    when(factory.from(request, plan, "price", "currency.keyword"))
        .thenThrow(new IllegalArgumentException("invalid mapping"));
    assertThatThrownBy(() -> provider.searchStructured(request, plan, "price", "currency.keyword"))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(client, mapper);
  }

  @Test
  void backendFailureIsWrapped() {
    var compiled = new OpenSearchSearchRequest(Map.of("match_all", Map.of()), 10);
    when(factory.from(request, plan, "price", "currency.keyword")).thenReturn(compiled);
    when(client.search(anyString(), anyMap())).thenThrow(new IllegalStateException("offline"));
    assertThatThrownBy(() -> provider.searchStructured(request, plan, "price", "currency.keyword"))
        .isInstanceOf(SearchProviderException.class)
        .hasCauseInstanceOf(IllegalStateException.class);
  }
}

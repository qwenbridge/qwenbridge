package io.qwenbridge.execution.provider.implementation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.qwenbridge.execution.provider.model.SearchRequest;
import io.qwenbridge.execution.provider.model.SearchResponse;
import io.qwenbridge.execution.provider.model.SearchResultSet;
import io.qwenbridge.execution.provider.opensearch.OpenSearchProperties;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchClient;
import io.qwenbridge.execution.provider.opensearch.dto.OpenSearchSearchRequest;
import io.qwenbridge.execution.provider.opensearch.mapper.OpenSearchResponseMapper;
import io.qwenbridge.execution.provider.opensearch.query.OpenSearchQueryFactory;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenSearchProviderTest {

  @Test
  void shouldSearchUsingOpenSearchClientAndMapResponse() {
    OpenSearchProperties properties =
        new OpenSearchProperties(
            "http://localhost:9200",
            "qwenbridge-products",
            10,
            Duration.ofSeconds(5),
            Duration.ofSeconds(30));

    OpenSearchClient client = mock(OpenSearchClient.class);
    OpenSearchQueryFactory queryFactory = mock(OpenSearchQueryFactory.class);
    OpenSearchResponseMapper responseMapper = mock(OpenSearchResponseMapper.class);

    SearchRequest request = SearchRequest.of("iphone");

    OpenSearchSearchRequest openSearchRequest =
        new OpenSearchSearchRequest(
            Map.of(
                "multi_match", Map.of("query", "iphone", "fields", List.of("title^3", "brand^2"))),
            10);

    Map<String, Object> rawResponse =
        Map.of(
            "took",
            5,
            "hits",
            Map.of(
                "total", Map.of("value", 0),
                "hits", List.of()));

    SearchResponse mappedResponse = new SearchResponse(new SearchResultSet(List.of(), 0, 5));

    when(queryFactory.from(request)).thenReturn(openSearchRequest);
    when(client.search(
            eq("qwenbridge-products"),
            eq(
                Map.of(
                    "query", openSearchRequest.query(),
                    "size", openSearchRequest.size()))))
        .thenReturn(rawResponse);
    when(responseMapper.from(rawResponse)).thenReturn(mappedResponse);

    OpenSearchProvider provider =
        new OpenSearchProvider(properties, client, queryFactory, responseMapper);

    SearchResponse response = provider.search(request);

    assertThat(response).isSameAs(mappedResponse);

    verify(queryFactory).from(request);
    verify(client)
        .search(
            eq("qwenbridge-products"),
            eq(
                Map.of(
                    "query", openSearchRequest.query(),
                    "size", openSearchRequest.size())));
    verify(responseMapper).from(rawResponse);
    verifyNoMoreInteractions(client, queryFactory, responseMapper);
  }

  @Test
  void shouldExposeProviderName() {
    OpenSearchProvider provider =
        new OpenSearchProvider(
            new OpenSearchProperties(
                "http://localhost:9200",
                "qwenbridge-products",
                10,
                Duration.ofSeconds(5),
                Duration.ofSeconds(30)),
            mock(OpenSearchClient.class),
            mock(OpenSearchQueryFactory.class),
            mock(OpenSearchResponseMapper.class));

    assertThat(provider.name()).isEqualTo("opensearch");
  }
}

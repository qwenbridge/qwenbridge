package io.qwenbridge.execution.provider.opensearch.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.qwenbridge.operations.metrics.OperationsMetrics;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenSearchClientTest {

  private final OpenSearchApiClient api = mock(OpenSearchApiClient.class);

  @Test
  void shouldPostSearchRequestAndParseResponse() {
    when(api.search(eq("products"), any()))
        .thenReturn(Map.of("took", 7, "hits", Map.of("total", Map.of("value", 1))));

    Map<String, Object> response = client().search("products", Map.of("query", Map.of()));

    assertThat(response).containsEntry("took", 7);
  }

  @Test
  void shouldPingOpenSearchRoot() {
    Response response = mock(Response.class);
    when(response.getStatus()).thenReturn(200);
    when(api.ping()).thenReturn(response);

    client().ping();
  }

  @Test
  void shouldThrowIllegalStateExceptionForSearchFailure() {
    Response response = mock(Response.class);
    when(response.getStatus()).thenReturn(503);
    WebApplicationException error = new WebApplicationException("http 503", response);
    when(api.search(any(), any())).thenThrow(error);

    OpenSearchClient client = client();

    assertThatThrownBy(() -> client.search("products", Map.of("query", Map.of())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("OpenSearch search failed");
  }

  private OpenSearchClient client() {
    return new OpenSearchClient(api, mock(OperationsMetrics.class));
  }
}

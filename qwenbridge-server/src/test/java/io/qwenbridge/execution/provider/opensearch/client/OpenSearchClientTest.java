package io.qwenbridge.execution.provider.opensearch.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.qwenbridge.operations.metrics.OperationsMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class OpenSearchClientTest {

  @Test
  void shouldExposeWebClient() {
    OpenSearchClient client =
        new OpenSearchClient(WebClient.builder().build(), mock(OperationsMetrics.class));

    assertThat(client.webClient()).isNotNull();
  }
}

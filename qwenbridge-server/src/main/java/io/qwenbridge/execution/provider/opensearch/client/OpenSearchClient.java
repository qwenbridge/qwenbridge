package io.qwenbridge.execution.provider.opensearch.client;

import io.qwenbridge.operations.metrics.OperationsMetrics;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class OpenSearchClient {

  private final WebClient openSearchWebClient;
  private final OperationsMetrics metrics;

  public OpenSearchClient(
      @Qualifier("openSearchWebClient") WebClient openSearchWebClient, OperationsMetrics metrics) {
    this.openSearchWebClient = openSearchWebClient;
    this.metrics = metrics;
  }

  public Map<String, Object> search(String index, Map<String, Object> query) {
    long started = System.nanoTime();
    try {
      Map<String, Object> response =
          openSearchWebClient
              .post()
              .uri("/{index}/_search", index)
              .bodyValue(query)
              .retrieve()
              .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
              .block();
      record("search", "success", started);
      return response;
    } catch (RuntimeException ex) {
      record("search", "failure", started);
      throw ex;
    }
  }

  public WebClient webClient() {
    return openSearchWebClient;
  }

  private void record(String operation, String outcome, long started) {
    metrics.recordOpenSearch(operation, outcome, Duration.ofNanos(System.nanoTime() - started));
  }
}

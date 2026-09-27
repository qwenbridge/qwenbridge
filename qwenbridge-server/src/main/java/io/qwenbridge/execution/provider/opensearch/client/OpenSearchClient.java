package io.qwenbridge.execution.provider.opensearch.client;

import io.qwenbridge.operations.metrics.OperationsMetrics;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
@Slf4j
public class OpenSearchClient {

  private final OpenSearchApiClient api;
  private final OperationsMetrics metrics;

  @Inject
  public OpenSearchClient(@RestClient OpenSearchApiClient api, OperationsMetrics metrics) {
    this.api = api;
    this.metrics = metrics;
  }

  public Map<String, Object> search(String index, Map<String, Object> query) {
    long started = System.nanoTime();
    try {
      Map<String, Object> result = api.search(index, query);
      record("search", "success", started);
      return result;
    } catch (WebApplicationException exception) {
      record("search", "failure", started);
      throw new IllegalStateException(
          "OpenSearch search failed. status=%s".formatted(status(exception)), exception);
    } catch (RuntimeException exception) {
      record("search", "failure", started);
      throw new IllegalStateException("OpenSearch search failed", exception);
    }
  }

  public void ping() {
    try (Response response = api.ping()) {
      if (response.getStatus() >= 400) {
        throw new IllegalStateException(
            "OpenSearch ping failed. status=%s".formatted(response.getStatus()));
      }
    } catch (WebApplicationException exception) {
      throw new IllegalStateException(
          "OpenSearch ping failed. status=%s".formatted(status(exception)), exception);
    } catch (IllegalStateException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new IllegalStateException("OpenSearch ping failed", exception);
    }
  }

  private int status(WebApplicationException exception) {
    Response response = exception.getResponse();
    return response != null ? response.getStatus() : -1;
  }

  private void record(String operation, String outcome, long started) {
    metrics.recordOpenSearch(
        operation, outcome, Duration.ofNanos(Math.max(0, System.nanoTime() - started)));
  }
}

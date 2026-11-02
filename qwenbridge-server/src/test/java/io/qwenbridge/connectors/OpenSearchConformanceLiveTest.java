package io.qwenbridge.connectors;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.core.retrieval.CancellationToken;
import io.qwenbridge.core.retrieval.ScoredDocument;
import io.qwenbridge.core.retrieval.SearchOutcome;
import io.qwenbridge.core.retrieval.SearchRequest;
import io.qwenbridge.core.retrieval.filter.Filter;
import io.qwenbridge.execution.provider.opensearch.OpenSearchProperties;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchApiClient;
import io.qwenbridge.execution.provider.opensearch.mapper.OpenSearchResponseMapper;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * QB-107 (OpenSearch): the production {@link OpenSearchBackend} on the unified SPI against a live
 * OpenSearch must return exactly the document set the reference semantics select, for the same
 * documents and filters as the Elasticsearch, PostgreSQL and MongoDB conformance test.
 *
 * <p>It works only in its own uniquely named index, created with the seed script's mapping and
 * deleted afterwards; existing indexes are never read or written.
 */
@EnabledIfEnvironmentVariable(named = "QWENBRIDGE_RUN_OPENSEARCH_IT", matches = "true")
class OpenSearchConformanceLiveTest {
  static final ObjectMapper JSON = new ObjectMapper();
  static final String INDEX = "qwenbridge-neutral-conformance-" + UUID.randomUUID();
  static final URI BASE =
      URI.create(
          // The plain OpenSearch the application itself is configured with; the authenticated TLS
          // test instance (QWENBRIDGE_TEST_OPENSEARCH_URL) belongs to the secure-transport test.
          Optional.ofNullable(System.getenv("QWENBRIDGE_SEARCH_OPENSEARCH_BASE_URL"))
              .orElse("http://localhost:9200"));
  static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  static OpenSearchBackend backend;

  @SuppressWarnings("unchecked")
  static Map<String, Object> call(String method, String path, Object body) {
    try {
      var request =
          HttpRequest.newBuilder(BASE.resolve(path))
              .timeout(Duration.ofSeconds(20))
              .header("Content-Type", "application/json")
              .method(
                  method,
                  body == null
                      ? HttpRequest.BodyPublishers.noBody()
                      : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
      var response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 300)
        throw new IllegalStateException(method + " " + path + " -> " + response.statusCode());
      return JSON.readValue(response.body(), Map.class);
    } catch (java.io.IOException ex) {
      throw new IllegalStateException(ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(ex);
    }
  }

  /** Transport for the production backend; only the calls this test needs. */
  static final class Client implements OpenSearchApiClient {
    public Map<String, Object> search(String index, Map<String, Object> query) {
      return call("POST", "/" + index + "/_search", query);
    }

    public Response ping() {
      throw new UnsupportedOperationException();
    }

    public Map<String, Object> createIndex(String index, Map<String, Object> mapping) {
      throw new UnsupportedOperationException();
    }

    public Map<String, Object> upsert(String index, String id, Map<String, Object> document) {
      return call("PUT", "/" + index + "/_doc/" + id + "?refresh=true", document);
    }

    public Map<String, Object> delete(String index, String id) {
      return call("DELETE", "/" + index + "/_doc/" + id + "?refresh=true", null);
    }
  }

  static Map<String, Object> textWithKeyword() {
    return Map.of(
        "type",
        "text",
        "fields",
        Map.of("keyword", Map.of("type", "keyword", "normalizer", "casefold")));
  }

  @BeforeAll
  static void start() {
    // Same field mapping as scripts/opensearch-seed.sh, without the vector field.
    var properties = new java.util.HashMap<String, Object>();
    for (String f : List.of("brand", "color", "size", "condition", "location"))
      properties.put(f, textWithKeyword());
    for (String f : List.of("tenantId", "documentId", "domain", "currency"))
      properties.put(f, Map.of("type", "keyword"));
    properties.put("category", Map.of("type", "keyword", "normalizer", "casefold"));
    properties.put("title", Map.of("type", "text"));
    properties.put("description", Map.of("type", "text"));
    properties.put("price", Map.of("type", "double"));
    properties.put("weightKg", Map.of("type", "double"));
    properties.put("inStock", Map.of("type", "boolean"));
    properties.put("availableAt", Map.of("type", "date"));
    call(
        "PUT",
        "/" + INDEX,
        Map.of(
            "settings",
            Map.of(
                "analysis",
                Map.of(
                    "normalizer",
                    Map.of("casefold", Map.of("type", "custom", "filter", List.of("lowercase"))))),
            "mappings",
            Map.of("properties", properties)));
    backend =
        new OpenSearchBackend(
            null,
            new Client(),
            new OpenSearchProperties(BASE.toString(), INDEX, 10, null, null),
            null,
            new OpenSearchResponseMapper());
    NeutralFilterConformance.DOCS.forEach(
        (id, d) -> backend.upsert(NeutralFilterConformance.TENANT, id, d));
    backend.upsert(
        NeutralFilterConformance.OTHER,
        "x1",
        NeutralFilterConformance.doc("Nike running shoes", "Nike", "black", "50", true, "mesh"));
  }

  @AfterAll
  static void stop() {
    call("DELETE", "/" + INDEX, null);
  }

  @Test
  void openSearchReturnsExactlyWhatTheReferenceSemanticsSelect() {
    for (Filter f : NeutralFilterConformance.FILTERS)
      assertThat(NeutralFilterConformance.actual(backend, f))
          .as("opensearch for " + f)
          .isEqualTo(NeutralFilterConformance.expected(f));
  }

  @Test
  void keywordTextNarrowsWithinTheTenantAndOutcomesAreTyped() {
    SearchOutcome o =
        backend.search(
            SearchRequest.keyword(NeutralFilterConformance.TENANT, "running", Filter.ALL, 50),
            CancellationToken.create());
    assertThat(((SearchOutcome.Success) o).hits())
        .extracting(ScoredDocument::id)
        .containsExactly("n1");
    var ids = new TreeSet<String>();
    ((SearchOutcome.Success) o).hits().forEach(h -> ids.add(h.id()));
    assertThat(ids).doesNotContain("x1");
    assertThat(
            backend.search(
                SearchRequest.keyword(NeutralFilterConformance.TENANT, "x", Filter.ALL, 500),
                CancellationToken.create()))
        .isInstanceOf(SearchOutcome.Unsupported.class);
  }
}

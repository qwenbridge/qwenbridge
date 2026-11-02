package io.qwenbridge.connectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.core.embedding.EmbeddingSpace;
import io.qwenbridge.core.retrieval.CancellationToken;
import io.qwenbridge.core.retrieval.QueryVector;
import io.qwenbridge.core.retrieval.ScoredDocument;
import io.qwenbridge.core.retrieval.SearchOutcome;
import io.qwenbridge.core.retrieval.SearchRequest;
import io.qwenbridge.core.retrieval.filter.FieldValue;
import io.qwenbridge.core.retrieval.filter.Filter;
import io.qwenbridge.execution.provider.opensearch.OpenSearchProperties;
import io.qwenbridge.execution.provider.opensearch.mapper.OpenSearchResponseMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * QB-087 live: the OpenSearch vector side of the unified SPI against the verification run's seeded
 * index (BGE-M3 vectors written by {@code scripts/opensearch-seed.sh}), with query vectors from the
 * same live Ollama model. Read-only: it only searches.
 */
@EnabledIfEnvironmentVariable(named = "QWENBRIDGE_RUN_OPENSEARCH_IT", matches = "true")
@EnabledIfEnvironmentVariable(named = "QWENBRIDGE_RUN_OLLAMA_IT", matches = "true")
class OpenSearchVectorLiveTest {
  static final ObjectMapper JSON = new ObjectMapper();
  static final EmbeddingSpace SPACE = new EmbeddingSpace("bge-m3", "1", 1024);

  static double[] embed(String text) throws Exception {
    String base =
        Optional.ofNullable(System.getenv("QWENBRIDGE_AI_OLLAMA_BASE_URL"))
            .orElse("http://localhost:11434");
    var response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create(base + "/api/embed"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(
                        HttpRequest.BodyPublishers.ofString(
                            JSON.writeValueAsString(Map.of("model", "bge-m3", "input", text))))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    var vector = JSON.readTree(response.body()).path("embeddings").get(0);
    double[] out = new double[vector.size()];
    for (int i = 0; i < out.length; i++) out[i] = vector.get(i).asDouble();
    return out;
  }

  static List<String> titles(SearchOutcome outcome) {
    assertThat(outcome).isInstanceOf(SearchOutcome.Success.class);
    return ((SearchOutcome.Success) outcome)
        .hits().stream()
            .map(ScoredDocument::fields)
            .map(f -> ((FieldValue.Text) f.get("title")).value())
            .toList();
  }

  @Test
  void meaningFindsTheRightProductWithinTheFilterAndTenant() throws Exception {
    String index = System.getenv("QWENBRIDGE_SEARCH_OPENSEARCH_INDEX");
    assumeTrue(index != null && !index.isBlank(), "no seeded verification index");
    var backend =
        new OpenSearchBackend(
            null,
            new OpenSearchConformanceLiveTest.Client(),
            new OpenSearchProperties(
                OpenSearchConformanceLiveTest.BASE.toString(), index, 10, null, null),
            null,
            new OpenSearchResponseMapper());
    var vector = backend.vector(SPACE);
    var query = new QueryVector(embed("headphones that block noise on a plane"), SPACE);
    var cancel = CancellationToken.create();

    assertThat(titles(vector.search(SearchRequest.vector("public", query, Filter.ALL, 3), cancel)))
        .first()
        .isEqualTo("Sony WH-1000XM5");
    // The neutral filter is applied inside the kNN search.
    var mice = new Filter.Compare("category", Filter.Compare.Op.EQ, FieldValue.text("mouse"));
    assertThat(titles(vector.search(SearchRequest.vector("public", query, mice, 3), cancel)))
        .isNotEmpty()
        .allSatisfy(t -> assertThat(t).containsAnyOf("Logitech", "Razer"));
    // Another tenant only ever sees its own document.
    assertThat(titles(vector.search(SearchRequest.vector("other", query, Filter.ALL, 5), cancel)))
        .containsExactly("Nike black running shoes size 42");
    // A query vector from another embedding space is refused, never compared.
    var foreign = new QueryVector(new double[] {1, 0, 0}, new EmbeddingSpace("other", "1", 3));
    assertThat(vector.search(SearchRequest.vector("public", foreign, Filter.ALL, 3), cancel))
        .isInstanceOf(SearchOutcome.Unsupported.class);
  }
}

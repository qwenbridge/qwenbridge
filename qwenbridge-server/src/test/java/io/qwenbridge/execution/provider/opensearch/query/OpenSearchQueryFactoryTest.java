package io.qwenbridge.execution.provider.opensearch.query;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.execution.provider.model.SearchRequest;
import io.qwenbridge.execution.provider.opensearch.OpenSearchProperties;
import io.qwenbridge.execution.provider.opensearch.dto.OpenSearchSearchRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenSearchQueryFactoryTest {

  @Test
  void shouldCreateMultiMatchQuery() {
    OpenSearchQueryFactory factory = new OpenSearchQueryFactory(properties());

    OpenSearchSearchRequest request = factory.from(SearchRequest.of("iphone"));

    assertThat(request).isNotNull();
    assertThat(request.size()).isEqualTo(25);
    assertThat(request.query()).containsKey("multi_match");

    Map<?, ?> multiMatch = (Map<?, ?>) request.query().get("multi_match");

    assertThat(multiMatch.get("query")).isEqualTo("iphone");
    assertThat(multiMatch.get("fields").toString())
        .contains("title^3")
        .contains("brand^2")
        .contains("category")
        .contains("description");
  }

  @Test
  void shouldCreateVectorQueryWhenEmbeddingIsPresent() {
    OpenSearchQueryFactory factory = new OpenSearchQueryFactory(properties());

    OpenSearchSearchRequest request =
        factory.from(SearchRequest.vector("wireless gaming mouse", List.of(0.1, 0.2, 0.3)));

    assertThat(request.query()).containsKey("knn");

    Map<?, ?> knn = (Map<?, ?>) request.query().get("knn");
    Map<?, ?> embedding = (Map<?, ?>) knn.get("embedding");

    assertThat(embedding.get("vector")).isEqualTo(List.of(0.1, 0.2, 0.3));
    assertThat(embedding.get("k")).isEqualTo(25);
  }

  @Test
  void shouldCreateHybridQueryWhenEmbeddingIsPresent() {
    OpenSearchQueryFactory factory = new OpenSearchQueryFactory(properties());

    OpenSearchSearchRequest request =
        factory.from(SearchRequest.hybrid("wireless gaming mouse", List.of(0.1, 0.2, 0.3)));

    assertThat(request.query()).containsKey("bool");

    Map<?, ?> bool = (Map<?, ?>) request.query().get("bool");
    List<?> should = (List<?>) bool.get("should");

    assertThat(bool.get("minimum_should_match")).isEqualTo(1);
    assertThat(should).hasSize(2);
    assertThat(should.get(0).toString()).contains("multi_match");
    assertThat(should.get(1).toString()).contains("knn");
    assertThat(request.query()).doesNotContainKey("hybrid");
  }

  @Test
  void shouldFallBackToKeywordQueryWhenVectorModeHasNoEmbedding() {
    OpenSearchQueryFactory factory = new OpenSearchQueryFactory(properties());

    OpenSearchSearchRequest request =
        factory.from(
            SearchRequest.withOptions(
                "iphone", Map.of(SearchRequest.OPTION_SEARCH_MODE, "VECTOR")));

    assertThat(request.query()).containsKey("multi_match");
    assertThat(request.query()).doesNotContainKey("knn");
  }

  private OpenSearchProperties properties() {
    return new OpenSearchProperties(
        "http://localhost:9200",
        "qwenbridge-products",
        25,
        Duration.ofSeconds(5),
        Duration.ofSeconds(30));
  }
}

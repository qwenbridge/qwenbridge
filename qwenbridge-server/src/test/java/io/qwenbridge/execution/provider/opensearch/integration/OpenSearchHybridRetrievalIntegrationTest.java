package io.qwenbridge.execution.provider.opensearch.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.ai.contract.EmbeddingRequest;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.execution.provider.implementation.OpenSearchProvider;
import io.qwenbridge.execution.provider.model.SearchRequest;
import io.qwenbridge.execution.provider.model.SearchResponse;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@QuarkusTest
@EnabledIfEnvironmentVariable(named = "QWENBRIDGE_RUN_OPENSEARCH_IT", matches = "true")
class OpenSearchHybridRetrievalIntegrationTest {

  @Inject AIService aiService;

  @Inject OpenSearchProvider openSearchProvider;

  @Test
  void shouldRetrieveRazerFirstForGamingMouseHybridSearch() {
    var embedding = aiService.embed(new EmbeddingRequest("gaming mouse razer esports"));

    SearchResponse response =
        openSearchProvider.search(SearchRequest.hybrid("razer gaming mouse", embedding.vector()));

    assertThat(response.results().hits()).isNotEmpty();

    var firstHit = response.results().hits().getFirst();

    assertThat(firstHit.id()).isEqualTo("product-5");
    assertThat(firstHit.document().get("title")).isEqualTo("Razer DeathAdder V3");
  }
}

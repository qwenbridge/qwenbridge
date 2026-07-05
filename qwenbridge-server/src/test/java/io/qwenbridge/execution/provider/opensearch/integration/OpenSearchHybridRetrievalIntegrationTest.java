package io.qwenbridge.execution.provider.opensearch.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.ai.contract.EmbeddingRequest;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.execution.provider.implementation.OpenSearchProvider;
import io.qwenbridge.execution.provider.model.SearchRequest;
import io.qwenbridge.execution.provider.model.SearchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "QWENBRIDGE_RUN_OPENSEARCH_IT", matches = "true")
class OpenSearchHybridRetrievalIntegrationTest {

  @Autowired private AIService aiService;

  @Autowired private OpenSearchProvider openSearchProvider;

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

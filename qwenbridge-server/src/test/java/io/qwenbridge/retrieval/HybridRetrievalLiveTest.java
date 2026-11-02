package io.qwenbridge.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.qwenbridge.ai.embedding.EmbeddingStages;
import io.qwenbridge.core.embedding.EmbeddingResult;
import io.qwenbridge.core.retrieval.IndexedDocument;
import io.qwenbridge.core.retrieval.filter.FieldValue;
import io.qwenbridge.core.retrieval.filter.Filter;
import io.qwenbridge.core.retrieval.hybrid.FusedDocument;
import io.qwenbridge.core.retrieval.hybrid.HybridResult;
import io.qwenbridge.core.retrieval.hybrid.HybridRetriever;
import io.qwenbridge.core.retrieval.memory.InMemoryKeywordIndex;
import io.qwenbridge.core.retrieval.memory.ManagedVectorIndex;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * QB-112, QB-116 with the real embedding model (opt-in: QWENBRIDGE_RUN_OLLAMA_IT=true). Vectors
 * come from the configured model through the server's embedding stage; nothing is mocked.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "QWENBRIDGE_RUN_OLLAMA_IT", matches = "true")
class HybridRetrievalLiveTest {

  @Inject EmbeddingStages embeddings;

  static final List<IndexedDocument> CATALOG =
      List.of(
          doc("p01", "Wireless noise cancelling headphones for travel"),
          doc("p02", "Leather office chair"),
          doc("p03", "Trail running shoes, waterproof"),
          doc("p04", "Cast iron frying pan"),
          doc("p05", "Mechanical gaming keyboard"),
          doc("p06", "Kids winter jacket"),
          doc("p07", "Espresso coffee machine"),
          doc("p08", "Bluetooth earbuds for the gym"));

  static IndexedDocument doc(String id, String text) {
    return new IndexedDocument("live", id, text, Map.of("title", new FieldValue.Text(text)));
  }

  @Test
  void realEmbeddingsFindMeaningAcrossLanguages() {
    var stage = embeddings.stage();
    var space = stage.space().orElseThrow();
    ManagedVectorIndex vectors = new ManagedVectorIndex("live-vector", space, stage, 100, 20);
    assertThat(vectors.upsert(CATALOG)).isInstanceOf(EmbeddingResult.Embedded.class);
    InMemoryKeywordIndex keyword = new InMemoryKeywordIndex("live-keyword", 100, 20);
    CATALOG.forEach(keyword::put);
    HybridRetriever retriever = new HybridRetriever(keyword, Optional.of(vectors), stage, 60);

    for (String query : List.of("headphones for the gym", "هدفون بی سیم", "hörlurar till gymmet")) {
      HybridResult r =
          retriever.retrieve(
              "live", query, Filter.ALL, 3, Duration.ofSeconds(60), HybridResult.Strategy.HYBRID);
      assertThat(r.executed())
          .as(query)
          .isIn(HybridResult.Strategy.HYBRID, HybridResult.Strategy.VECTOR_ONLY);
      assertThat(r.documents().stream().map(FusedDocument::id).toList())
          .as(query)
          .containsAnyOf("p01", "p08");
    }
  }
}

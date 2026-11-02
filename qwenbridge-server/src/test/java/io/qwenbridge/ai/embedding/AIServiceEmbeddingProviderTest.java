package io.qwenbridge.ai.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.qwenbridge.ai.contract.EmbeddingResponse;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.core.embedding.EmbeddingResult;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** QB-110, QB-111: the AI-service embedding provider behind the Core SPI and stage. */
class AIServiceEmbeddingProviderTest {

  private final AIService ai = mock(AIService.class);

  private EmbeddingStages stages(int dimensions) {
    return new EmbeddingStages(ai, true, "bge-m3", "1", dimensions, Duration.ofSeconds(2));
  }

  @Test
  void vectorsInTheDeclaredSpaceAreReturned() {
    when(ai.embed(any())).thenReturn(new EmbeddingResponse(List.of(0.1, 0.2, 0.3)));
    var result = stages(3).stage().embed(List.of("shoes", "boots"));
    var embedded = (EmbeddingResult.Embedded) result;
    assertThat(embedded.vectors()).hasSize(2);
    assertThat(embedded.space().toString()).isEqualTo("bge-m3@1/3");
  }

  @Test
  void aModelThatChangedItsDimensionsIsDetectedNotMixedIn() {
    when(ai.embed(any())).thenReturn(new EmbeddingResponse(List.of(0.1, 0.2, 0.3)));
    assertThat(stages(1024).stage().embed(List.of("shoes")))
        .isEqualTo(
            new EmbeddingResult.Failed(
                EmbeddingResult.Failure.INVALID_OUTPUT, AIServiceEmbeddingProvider.ID));
  }

  @Test
  void anUnavailableModelIsATypedFailureAndDisabledMeansNoProvider() {
    when(ai.embed(any())).thenThrow(new IllegalStateException("ollama down"));
    assertThat(((EmbeddingResult.Failed) stages(3).stage().embed(List.of("x"))).failure())
        .isEqualTo(EmbeddingResult.Failure.UNAVAILABLE);
    var disabled = new EmbeddingStages(ai, false, "bge-m3", "1", 3, Duration.ofSeconds(1));
    assertThat(((EmbeddingResult.Failed) disabled.stage().embed(List.of("x"))).failure())
        .isEqualTo(EmbeddingResult.Failure.NO_PROVIDER);
    assertThat(disabled.stage().space()).isEmpty();
  }
}

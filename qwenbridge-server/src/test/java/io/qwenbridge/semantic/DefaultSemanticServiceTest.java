package io.qwenbridge.semantic;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.semantic.ai.AISemanticService;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultSemanticServiceTest {

  @Test
  void shouldReturnAISemanticAnalysisWhenAISucceeds() {
    AISemanticService aiSemanticService =
        query ->
            new SemanticAnalysis(
                query,
                "cheap iphone",
                "User wants an affordable iPhone.",
                List.of(new SemanticEntity("iphone", SemanticEntityType.PRODUCT, 0.95)),
                List.of("product_search"),
                SemanticAmbiguity.none(),
                0.9);

    DefaultSemanticService service = new DefaultSemanticService(aiSemanticService);

    SemanticAnalysis analysis = service.analyze("cheap iphone");

    assertThat(analysis.originalQuery()).isEqualTo("cheap iphone");
    assertThat(analysis.normalizedQuery()).isEqualTo("cheap iphone");
    assertThat(analysis.semanticMeaning()).isEqualTo("User wants an affordable iPhone.");
    assertThat(analysis.entities()).hasSize(1);
    assertThat(analysis.confidence()).isEqualTo(0.9);
  }

  @Test
  void shouldFallbackToBasicSemanticAnalysisWhenAIFails() {
    AISemanticService aiSemanticService =
        query -> {
          throw new RuntimeException("ollama unavailable");
        };

    DefaultSemanticService service = new DefaultSemanticService(aiSemanticService);

    SemanticAnalysis analysis = service.analyze(" Cheap iPhone ");

    assertThat(analysis.originalQuery()).isEqualTo(" Cheap iPhone ");
    assertThat(analysis.normalizedQuery()).isEqualTo("cheap iphone");
    assertThat(analysis.semanticMeaning()).isEqualTo("Cheap iPhone");
    assertThat(analysis.entities()).isEmpty();
    assertThat(analysis.confidence()).isEqualTo(0.5);
  }
}

package io.qwenbridge.semantic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticAnalysisTest {

    @Test
    void shouldCreateBasicSemanticAnalysis() {
        SemanticAnalysis analysis = SemanticAnalysis.basic(" Cheap iPhone ");

        assertThat(analysis.originalQuery()).isEqualTo(" Cheap iPhone ");
        assertThat(analysis.normalizedQuery()).isEqualTo("cheap iphone");
        assertThat(analysis.semanticMeaning()).isEqualTo("Cheap iPhone");
        assertThat(analysis.entities()).isEmpty();
        assertThat(analysis.domainHints()).isEmpty();
        assertThat(analysis.ambiguity().ambiguous()).isFalse();
        assertThat(analysis.confidence()).isEqualTo(0.5);
    }

    @Test
    void shouldRejectInvalidConfidence() {
        assertThatThrownBy(() -> new SemanticAnalysis(
                "iphone",
                "iphone",
                "iphone search",
                List.of(),
                List.of(),
                SemanticAmbiguity.none(),
                1.5
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("confidence must be between 0.0 and 1.0");
    }

    @Test
    void shouldCreateEntityWithTypeAndConfidence() {
        SemanticEntity entity = new SemanticEntity("iphone", SemanticEntityType.PRODUCT, 0.9);

        assertThat(entity.value()).isEqualTo("iphone");
        assertThat(entity.type()).isEqualTo(SemanticEntityType.PRODUCT);
        assertThat(entity.confidence()).isEqualTo(0.9);
    }
}

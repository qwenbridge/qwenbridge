package io.qwenbridge.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.qwenbridge.ai.provider.ollama.dto.OllamaEmbeddingResponse;
import io.qwenbridge.threat.ThreatResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResponseImmutabilityTest {
  @Test
  void callersCannotRemoveThreatEvidenceAfterTheDecision() {
    var input = new ArrayList<>(List.of("SQL injection"));
    var result = ThreatResult.detected(input);
    input.clear();
    assertThat(result.reasons()).containsExactly("SQL injection");
    assertThatThrownBy(() -> result.reasons().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void embeddingOwnsBothTheOuterAndInnerLists() {
    var vector = new ArrayList<>(List.of(.1, .2));
    var input = new ArrayList<List<Double>>();
    input.add(vector);
    var response = new OllamaEmbeddingResponse(input);
    vector.clear();
    input.clear();
    assertThat(response.firstEmbedding()).containsExactly(.1, .2);
    assertThatThrownBy(() -> response.firstEmbedding().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void responseDoesNotAliasMutableRewriteCollections() {
    var rewrites = new ArrayList<>(List.of("gaming mouse"));
    var response = SearchAnalyzeResponse.builder().rewrites(rewrites).build();
    rewrites.clear();
    assertThat(response.rewrites()).containsExactly("gaming mouse");
  }
}

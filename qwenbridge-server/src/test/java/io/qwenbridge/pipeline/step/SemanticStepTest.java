package io.qwenbridge.pipeline.step;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchMode;
import io.qwenbridge.intent.IntentType;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.SemanticResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class SemanticStepTest {

  @Test
  void shouldMapSemanticResultFromSearchAnalysis() {
    ExecutionContext context = new ExecutionContext("cheap iphone");
    context.store(SearchAnalysis.class, analysis());

    SemanticResult result = new SemanticStep().execute(context);

    assertThat(result.validated()).isTrue();
    assertThat(result.score()).isEqualTo(0.9);
    assertThat(result.analysis()).isNotNull();
    assertThat(result.analysis().originalQuery()).isEqualTo("cheap iphone");
    assertThat(result.analysis().entities()).hasSize(1);
  }

  private static SearchAnalysis analysis() {
    return SearchAnalysis.builder()
        .language("en")
        .intent(IntentType.PRODUCT_SEARCH)
        .intentConfidence(0.85)
        .intentReason("User searches for a product.")
        .rewrites(List.of("cheap iphone"))
        .semanticValidated(true)
        .semanticScore(0.90)
        .semanticMeaning("User wants an affordable iPhone.")
        .entities(List.of("iphone"))
        .searchMode(SearchMode.KEYWORD)
        .backend(SearchBackend.IN_MEMORY)
        .keywordSearch(true)
        .vectorSearch(false)
        .hybridSearch(false)
        .facets(true)
        .rerank(false)
        .rewriteAgain(false)
        .answer(false)
        .decisionConfidence(0.80)
        .decisionReason("Keyword search is enough.")
        .build();
  }
}

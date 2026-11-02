package io.qwenbridge.analysis.ai;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.analysis.parser.SearchAnalysisJsonParser;
import io.qwenbridge.analysis.prompt.SearchAnalysisPromptBuilder;
import io.qwenbridge.intent.IntentType;
import jakarta.inject.Inject;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@QuarkusTest
@EnabledIfEnvironmentVariable(named = "QWENBRIDGE_RUN_OLLAMA_IT", matches = "true")
class CompactAnalysisQualityIntegrationTest {
  @Inject AIService ai;
  @Inject SearchAnalysisPromptBuilder prompt;
  @Inject SearchAnalysisJsonParser parser;

  private io.qwenbridge.analysis.model.SearchAnalysis analyze(String query) {
    return parser.parse(
        ai.chat(new ChatRequest(prompt.build(query), prompt.outputSchema())).content(), query);
  }

  @Test
  void preservesEnglishNegationAndBudget() {
    var analysis = analyze("wireless headphones not refurbished under 100 EUR");
    assertThat(analysis.intent()).isEqualTo(IntentType.PRODUCT_SEARCH);
    String rewrite = String.join(" ", analysis.rewrites()).toLowerCase(Locale.ROOT);
    assertThat(rewrite).contains("100");
    assertThat(rewrite).containsAnyOf("eur", "euro");
    assertThat(rewrite)
        .containsAnyOf("not refurbished", "non-refurbished", "non refurbished", "no refurbished");
    assertThat(analysis.semanticValidated()).isTrue();
  }

  @Test
  void translatesPersianWithoutDroppingTheAmount() {
    var analysis = analyze("بهترین لپ تاپ گیمینگ زیر ۱۵۰۰ یورو");
    String rewrite = String.join(" ", analysis.rewrites()).toLowerCase(Locale.ROOT);
    assertThat(rewrite).contains("1500");
    assertThat(rewrite).containsAnyOf("eur", "euro");
    assertThat(rewrite).containsAnyOf("under", "below", "less than");
    assertThat(analysis.semanticValidated()).isTrue();
  }

  @Test
  void comparisonDoesNotBecomeOrdinaryProductSearch() {
    var analysis = analyze("compare Sony and Bose headphones");
    assertThat(analysis.intent()).isEqualTo(IntentType.COMPARE);
    assertThat(String.join(" ", analysis.rewrites()).toLowerCase(Locale.ROOT))
        .contains("sony", "bose");
  }
}

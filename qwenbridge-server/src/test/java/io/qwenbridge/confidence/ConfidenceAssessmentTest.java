package io.qwenbridge.confidence;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.intent.IntentType;
import org.junit.jupiter.api.Test;

class ConfidenceAssessmentTest {
  private final ConfidenceService service = new ConfidenceService();

  @Test
  void missingAndFallbackAnalysisAreNotTrusted() {
    assertThat(service.assess(null).value()).isZero();
    assertThat(service.assess(SearchAnalysis.fallback("desk")).value()).isZero();
  }

  @Test
  void weakestSignalBoundsTheConfidence() {
    var analysis =
        SearchAnalysis.builder()
            .intent(IntentType.PRODUCT_SEARCH)
            .intentConfidence(.9)
            .semanticValidated(true)
            .semanticScore(.95)
            .decisionConfidence(.2)
            .build();
    var result = service.assess(analysis);
    assertThat(result.value()).isEqualTo(.2);
    assertThat(result.calibrated()).isFalse();
    assertThat(result.signals()).containsKeys("intent", "semantic", "decision");
  }

  @Test
  void nonFiniteSignalsCannotRaiseConfidence() {
    var analysis =
        SearchAnalysis.builder()
            .intent(IntentType.PRODUCT_SEARCH)
            .intentConfidence(Double.NaN)
            .semanticValidated(true)
            .semanticScore(Double.POSITIVE_INFINITY)
            .decisionConfidence(.9)
            .build();
    assertThat(service.assess(analysis).value()).isZero();
  }
}

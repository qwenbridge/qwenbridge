package io.qwenbridge.intent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IntentAnalysisTest {

  @Test
  void shouldCreateProductSearchIntent() {
    IntentAnalysis analysis = IntentAnalysis.productSearch();

    assertThat(analysis.type()).isEqualTo(IntentType.PRODUCT_SEARCH);
    assertThat(analysis.reason()).isEqualTo("Default product search intent.");
    assertThat(analysis.confidence()).isEqualTo(0.5);
  }

  @Test
  void shouldFallbackToUnknownForInvalidIntentType() {
    assertThat(IntentType.from("PRODUCT_SEARCH")).isEqualTo(IntentType.PRODUCT_SEARCH);
    assertThat(IntentType.from("PRODUCT_SEARCH|FILTER")).isEqualTo(IntentType.UNKNOWN);
    assertThat(IntentType.from(null)).isEqualTo(IntentType.UNKNOWN);
  }

  @Test
  void shouldFallbackToUnknownTypeWhenTypeIsNull() {
    IntentAnalysis analysis = new IntentAnalysis(null, "reason", 0.4);

    assertThat(analysis.type()).isEqualTo(IntentType.UNKNOWN);
  }

  @Test
  void shouldFallbackToDefaultReasonWhenReasonIsBlank() {
    IntentAnalysis analysis = new IntentAnalysis(IntentType.PRODUCT_SEARCH, " ", 0.7);

    assertThat(analysis.reason()).isEqualTo("No intent reason provided.");
  }

  @Test
  void shouldRejectInvalidConfidence() {
    assertThatThrownBy(() -> new IntentAnalysis(IntentType.PRODUCT_SEARCH, "reason", 1.5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("confidence must be between 0.0 and 1.0");
  }
}

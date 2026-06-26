package io.qwenbridge.intent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntentAnalysisTest {

    @Test
    void shouldCreateProductSearchIntent() {
        IntentAnalysis analysis = IntentAnalysis.productSearch();

        assertThat(analysis.type()).isEqualTo(IntentType.PRODUCT_SEARCH);
        assertThat(analysis.confidence()).isEqualTo(0.5);
    }

    @Test
    void shouldFallbackToUnknownForInvalidIntentType() {
        assertThat(IntentType.from("PRODUCT_SEARCH")).isEqualTo(IntentType.PRODUCT_SEARCH);
        assertThat(IntentType.from("PRODUCT_SEARCH|FILTER")).isEqualTo(IntentType.UNKNOWN);
        assertThat(IntentType.from(null)).isEqualTo(IntentType.UNKNOWN);
    }

    @Test
    void shouldRejectInvalidConfidence() {
        assertThatThrownBy(() -> new IntentAnalysis(
                IntentType.PRODUCT_SEARCH,
                "reason",
                1.5
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("confidence must be between 0.0 and 1.0");
    }
}

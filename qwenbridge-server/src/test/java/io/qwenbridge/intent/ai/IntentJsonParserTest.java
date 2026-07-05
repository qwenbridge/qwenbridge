package io.qwenbridge.intent.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.intent.IntentAnalysis;
import io.qwenbridge.intent.IntentType;
import org.junit.jupiter.api.Test;

class IntentJsonParserTest {

  @Test
  void shouldParseIntentAnalysisJson() {
    IntentJsonParser parser = new IntentJsonParser(new ObjectMapper());

    String json =
        """
        {
          "type": "PRODUCT_SEARCH",
          "reason": "User is searching for a product.",
          "confidence": 0.9
        }
        """;

    IntentAnalysis analysis = parser.parse(json);

    assertThat(analysis.type()).isEqualTo(IntentType.PRODUCT_SEARCH);
    assertThat(analysis.reason()).isEqualTo("User is searching for a product.");
    assertThat(analysis.confidence()).isEqualTo(0.9);
  }

  @Test
  void shouldParseIntentAnalysisJsonWrappedInMarkdownFence() {
    IntentJsonParser parser = new IntentJsonParser(new ObjectMapper());

    String json =
        """
        ```json
        {
          "type": "FILTER",
          "reason": "User is narrowing existing results.",
          "confidence": 0.82
        }
        ```
        """;

    IntentAnalysis analysis = parser.parse(json);

    assertThat(analysis.type()).isEqualTo(IntentType.FILTER);
    assertThat(analysis.reason()).isEqualTo("User is narrowing existing results.");
    assertThat(analysis.confidence()).isEqualTo(0.82);
  }
}

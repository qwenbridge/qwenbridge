package io.qwenbridge.intent.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IntentPromptBuilderTest {

  @Test
  void shouldBuildPromptContainingQueryAndJsonContract() {
    IntentPromptBuilder builder = new IntentPromptBuilder();

    String prompt = builder.build("cheap iphone");

    assertThat(prompt).contains("cheap iphone");
    assertThat(prompt).contains("Return only valid JSON");
    assertThat(prompt).contains("Do not wrap the response in markdown");
    assertThat(prompt).contains("PRODUCT_SEARCH");
    assertThat(prompt).contains("NAVIGATION");
    assertThat(prompt).contains("FILTER");
    assertThat(prompt).contains("COMPARE");
    assertThat(prompt).contains("UNKNOWN");
  }
}

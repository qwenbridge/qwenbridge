package io.qwenbridge.decision.ai;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.pipeline.ExecutionContext;
import org.junit.jupiter.api.Test;

class DecisionPromptBuilderTest {

  @Test
  void shouldBuildSearchDecisionPrompt() {
    ExecutionContext context = new ExecutionContext("cheap iphone");

    String prompt = new DecisionPromptBuilder().build(context);

    assertThat(prompt).contains("AI Search Decision Engine");
    assertThat(prompt).contains("cheap iphone");
    assertThat(prompt).contains("KEYWORD");
    assertThat(prompt).contains("HYBRID");
    assertThat(prompt).contains("DIRECT_ANSWER");
    assertThat(prompt).contains("Return only valid JSON");
    assertThat(prompt).contains("Stay strictly within search orchestration");
  }
}

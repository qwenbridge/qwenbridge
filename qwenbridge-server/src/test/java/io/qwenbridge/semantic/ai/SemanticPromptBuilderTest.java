package io.qwenbridge.semantic.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticPromptBuilderTest {

    @Test
    void shouldBuildPromptContainingQueryAndJsonContract() {
        SemanticPromptBuilder builder = new SemanticPromptBuilder();

        String prompt = builder.build("cheap iphone for my son");

        assertThat(prompt).contains("cheap iphone for my son");
        assertThat(prompt).contains("Return only valid JSON");
        assertThat(prompt).contains("Do not wrap the response in markdown");
        assertThat(prompt).contains("originalQuery");
        assertThat(prompt).contains("normalizedQuery");
        assertThat(prompt).contains("semanticMeaning");
        assertThat(prompt).contains("entities");
        assertThat(prompt).contains("domainHints");
        assertThat(prompt).contains("ambiguity");
        assertThat(prompt).contains("confidence");
    }

    @Test
    void shouldTellAIToUseExactlyOneValidEntityType() {
        SemanticPromptBuilder builder = new SemanticPromptBuilder();

        String prompt = builder.build("table");

        assertThat(prompt).contains("type must be exactly one of");
        assertThat(prompt).contains("type must never contain multiple values");
        assertThat(prompt).contains("never return values like PRODUCT|CATEGORY");
        assertThat(prompt).contains("if unsure, use UNKNOWN");
    }
}

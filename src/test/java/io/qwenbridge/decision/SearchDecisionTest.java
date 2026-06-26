package io.qwenbridge.decision;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchDecisionTest {

    @Test
    void shouldCreateKeywordDecision() {
        SearchDecision decision = SearchDecision.keyword();

        assertThat(decision.mode()).isEqualTo(SearchMode.KEYWORD);
        assertThat(decision.backend()).isEqualTo(SearchBackend.IN_MEMORY);
        assertThat(decision.keywordSearch()).isTrue();
        assertThat(decision.vectorSearch()).isFalse();
        assertThat(decision.answer()).isFalse();
        assertThat(decision.confidence()).isEqualTo(0.70);
    }

    @Test
    void shouldCreateHybridDecision() {
        SearchDecision decision = SearchDecision.hybrid();

        assertThat(decision.mode()).isEqualTo(SearchMode.HYBRID);
        assertThat(decision.keywordSearch()).isTrue();
        assertThat(decision.vectorSearch()).isTrue();
        assertThat(decision.hybridSearch()).isTrue();
        assertThat(decision.rerank()).isTrue();
    }

    @Test
    void shouldCreateDirectAnswerDecision() {
        SearchDecision decision = SearchDecision.directAnswer();

        assertThat(decision.mode()).isEqualTo(SearchMode.DIRECT_ANSWER);
        assertThat(decision.backend()).isEqualTo(SearchBackend.NONE);
        assertThat(decision.answer()).isTrue();
        assertThat(decision.keywordSearch()).isFalse();
        assertThat(decision.vectorSearch()).isFalse();
    }

    @Test
    void shouldRejectInvalidConfidence() {
        assertThatThrownBy(() -> new SearchDecision(
                SearchMode.KEYWORD,
                SearchBackend.IN_MEMORY,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                1.5,
                "invalid"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("confidence must be between 0.0 and 1.0");
    }

    @Test
    void shouldFallbackBlankReason() {
        SearchDecision decision = new SearchDecision(
                SearchMode.KEYWORD,
                SearchBackend.IN_MEMORY,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                0.5,
                " "
        );

        assertThat(decision.reason()).isEqualTo("No decision reason provided.");
    }
}

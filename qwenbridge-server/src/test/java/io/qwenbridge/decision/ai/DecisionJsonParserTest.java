package io.qwenbridge.decision.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchDecision;
import io.qwenbridge.decision.SearchMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DecisionJsonParserTest {

    private final DecisionJsonParser parser = new DecisionJsonParser(new ObjectMapper());

    @Test
    void shouldParsePlainDecisionJson() {
        SearchDecision decision = parser.parse("""
                {
                  "mode": "HYBRID",
                  "backend": "IN_MEMORY",
                  "keywordSearch": true,
                  "vectorSearch": true,
                  "hybridSearch": true,
                  "facets": true,
                  "rerank": true,
                  "rewriteAgain": false,
                  "answer": false,
                  "confidence": 0.88,
                  "reason": "Hybrid search is useful for semantic product matching."
                }
                """);

        assertThat(decision.mode()).isEqualTo(SearchMode.HYBRID);
        assertThat(decision.backend()).isEqualTo(SearchBackend.IN_MEMORY);
        assertThat(decision.keywordSearch()).isTrue();
        assertThat(decision.vectorSearch()).isTrue();
        assertThat(decision.confidence()).isEqualTo(0.88);
    }

    @Test
    void shouldParseFencedDecisionJson() {
        SearchDecision decision = parser.parse("""
                ```json
                {
                  "mode": "KEYWORD",
                  "backend": "IN_MEMORY",
                  "keywordSearch": true,
                  "vectorSearch": false,
                  "hybridSearch": false,
                  "facets": true,
                  "rerank": false,
                  "rewriteAgain": false,
                  "answer": false,
                  "confidence": 0.72,
                  "reason": "Exact product search."
                }
                ```
                """);

        assertThat(decision.mode()).isEqualTo(SearchMode.KEYWORD);
        assertThat(decision.backend()).isEqualTo(SearchBackend.IN_MEMORY);
    }

    @Test
    void shouldRejectInvalidJson() {
        assertThatThrownBy(() -> parser.parse("not json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failed to parse search decision JSON");
    }
}

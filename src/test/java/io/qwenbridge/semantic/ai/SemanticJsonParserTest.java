package io.qwenbridge.semantic.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.semantic.SemanticAnalysis;
import io.qwenbridge.semantic.SemanticEntityType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticJsonParserTest {

    @Test
    void shouldParseSemanticAnalysisJson() {
        SemanticJsonParser parser = new SemanticJsonParser(new ObjectMapper());

        String json = """
                {
                  "originalQuery": "cheap iphone for my son",
                  "normalizedQuery": "cheap iphone for my son",
                  "semanticMeaning": "User wants an affordable iPhone suitable for a child.",
                  "entities": [
                    {
                      "value": "iphone",
                      "type": "PRODUCT",
                      "confidence": 0.95
                    }
                  ],
                  "domainHints": ["product_search"],
                  "ambiguity": {
                    "ambiguous": false,
                    "possibleMeanings": []
                  },
                  "confidence": 0.9
                }
                """;

        SemanticAnalysis analysis = parser.parse(json);

        assertThat(analysis.originalQuery()).isEqualTo("cheap iphone for my son");
        assertThat(analysis.normalizedQuery()).isEqualTo("cheap iphone for my son");
        assertThat(analysis.semanticMeaning()).isEqualTo("User wants an affordable iPhone suitable for a child.");
        assertThat(analysis.entities()).hasSize(1);
        assertThat(analysis.entities().getFirst().value()).isEqualTo("iphone");
        assertThat(analysis.entities().getFirst().type()).isEqualTo(SemanticEntityType.PRODUCT);
        assertThat(analysis.domainHints()).containsExactly("product_search");
        assertThat(analysis.ambiguity().ambiguous()).isFalse();
        assertThat(analysis.confidence()).isEqualTo(0.9);
    }
}

package io.qwenbridge.semantic.ai;

import org.springframework.stereotype.Component;

@Component
public class SemanticPromptBuilder {

    public String build(String query) {
        return """
                Analyze the semantic meaning of the following search query.

                Return only valid JSON with this structure:
                {
                  "originalQuery": "...",
                  "normalizedQuery": "...",
                  "semanticMeaning": "...",
                  "entities": [
                    {
                      "value": "...",
                      "type": "PRODUCT|BRAND|CATEGORY|ATTRIBUTE|PRICE|LOCATION|LANGUAGE|UNKNOWN",
                      "confidence": 0.0
                    }
                  ],
                  "domainHints": ["..."],
                  "ambiguity": {
                    "ambiguous": false,
                    "possibleMeanings": []
                  },
                  "confidence": 0.0
                }

                Query:
                %s
                """.formatted(query);
    }
}

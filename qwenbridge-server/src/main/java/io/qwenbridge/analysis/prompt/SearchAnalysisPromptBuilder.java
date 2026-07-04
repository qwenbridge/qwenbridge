package io.qwenbridge.analysis.prompt;

import org.springframework.stereotype.Component;

@Component
public class SearchAnalysisPromptBuilder {

    public String build(String query) {
        return """
                You are QwenBridge Search Analysis Engine.

                Analyze the user search query and return ONLY valid JSON.

                Required JSON fields:
                {
                  "language": "en|fa|unknown",
                  "intent": "PRODUCT_SEARCH|NAVIGATION|FILTER|COMPARE|UNKNOWN",
                  "intentConfidence": 0.0,
                  "intentReason": "...",
                  "rewrites": ["..."],
                  "semanticValidated": true,
                  "semanticScore": 0.0,
                  "semanticMeaning": "...",
                  "entities": ["..."],
                  "searchMode": "KEYWORD|SEMANTIC|VECTOR|HYBRID|DIRECT_ANSWER",
                  "backend": "OPENSEARCH|IN_MEMORY|NONE|CUSTOM",
                  "keywordSearch": true,
                  "vectorSearch": false,
                  "hybridSearch": false,
                  "facets": true,
                  "rerank": false,
                  "rewriteAgain": false,
                  "answer": false,
                  "decisionConfidence": 0.0,
                  "decisionReason": "..."
                }

                Query:
                %s
                """.formatted(query);
    }
}

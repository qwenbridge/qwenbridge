package io.qwenbridge.decision.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.decision.SearchDecision;
import org.springframework.stereotype.Component;

@Component
public class DecisionJsonParser {

    private final ObjectMapper objectMapper;

    public DecisionJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SearchDecision parse(String content) {
        try {
            return objectMapper.readValue(clean(content), SearchDecision.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to parse search decision JSON", e);
        }
    }

    private String clean(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("decision JSON content must not be blank");
        }

        String cleaned = content.trim();

        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');

        if (start < 0 || end < start) {
            throw new IllegalArgumentException("decision JSON object not found");
        }

        return cleaned.substring(start, end + 1).trim();
    }
}

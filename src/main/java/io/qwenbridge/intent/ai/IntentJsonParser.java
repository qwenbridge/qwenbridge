package io.qwenbridge.intent.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.intent.IntentAnalysis;
import org.springframework.stereotype.Component;

@Component
public class IntentJsonParser {

    private final ObjectMapper objectMapper;

    public IntentJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public IntentAnalysis parse(String json) {
        try {
            return objectMapper.readValue(sanitize(json), IntentAnalysis.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("failed to parse intent analysis JSON", exception);
        }
    }

    private String sanitize(String json) {
        if (json == null) {
            throw new IllegalArgumentException("intent analysis JSON must not be null");
        }

        String sanitized = json.trim();

        if (sanitized.startsWith("```json")) {
            sanitized = sanitized.substring("```json".length()).trim();
        } else if (sanitized.startsWith("```")) {
            sanitized = sanitized.substring("```".length()).trim();
        }

        if (sanitized.endsWith("```")) {
            sanitized = sanitized.substring(0, sanitized.length() - "```".length()).trim();
        }

        return sanitized;
    }
}

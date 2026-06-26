package io.qwenbridge.semantic.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.semantic.SemanticAnalysis;
import org.springframework.stereotype.Component;

@Component
public class SemanticJsonParser {

    private final ObjectMapper objectMapper;

    public SemanticJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SemanticAnalysis parse(String json) {
        try {
            return objectMapper.readValue(sanitize(json), SemanticAnalysis.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("failed to parse semantic analysis JSON", exception);
        }
    }

    private String sanitize(String json) {
        if (json == null) {
            throw new IllegalArgumentException("semantic analysis JSON must not be null");
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

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
            return objectMapper.readValue(json, SemanticAnalysis.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("failed to parse semantic analysis JSON", exception);
        }
    }
}

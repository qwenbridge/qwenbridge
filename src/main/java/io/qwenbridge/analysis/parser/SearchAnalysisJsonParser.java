package io.qwenbridge.analysis.parser;

import lombok.RequiredArgsConstructor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchMode;
import io.qwenbridge.intent.IntentType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SearchAnalysisJsonParser {

    private final ObjectMapper objectMapper;

    public SearchAnalysis parse(String content, String originalQuery) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(content));

            return new SearchAnalysis(
                    text(root, "language", "unknown"),
                    IntentType.from(text(root, "intent", "UNKNOWN")),
                    decimal(root, "intentConfidence", 0.0),
                    text(root, "intentReason", "No intent reason provided."),
                    array(root, "rewrites"),
                    bool(root, "semanticValidated", false),
                    decimal(root, "semanticScore", 0.0),
                    text(root, "semanticMeaning", originalQuery),
                    array(root, "entities"),
                    enumValue(SearchMode.class, text(root, "searchMode", "KEYWORD"), SearchMode.KEYWORD),
                    enumValue(SearchBackend.class, text(root, "backend", "OPENSEARCH"), SearchBackend.OPENSEARCH),
                    bool(root, "keywordSearch", true),
                    bool(root, "vectorSearch", false),
                    bool(root, "hybridSearch", false),
                    bool(root, "facets", true),
                    bool(root, "rerank", false),
                    bool(root, "rewriteAgain", false),
                    bool(root, "answer", false),
                    decimal(root, "decisionConfidence", 0.5),
                    text(root, "decisionReason", "AI search analysis decision.")
            );
        } catch (Exception ignored) {
            return SearchAnalysis.fallback(originalQuery);
        }
    }

    private String extractJson(String content) {
        if (content == null || content.isBlank()) {
            return "{}";
        }

        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');

        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }

        return content;
    }

    private String text(JsonNode root, String field, String fallback) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? fallback : node.asText(fallback);
    }

    private boolean bool(JsonNode root, String field, boolean fallback) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? fallback : node.asBoolean(fallback);
    }

    private double decimal(JsonNode root, String field, double fallback) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? fallback : node.asDouble(fallback);
    }

    private List<String> array(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isArray()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            if (item != null && !item.isNull() && !item.asText().isBlank()) {
                values.add(item.asText());
            }
        });
        return values;
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}

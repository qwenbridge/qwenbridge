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

            return SearchAnalysis.builder()
                    .language(text(root, "language", "unknown"))
                    .intent(IntentType.from(text(root, "intent", "UNKNOWN")))
                    .intentConfidence(decimal(root, "intentConfidence", 0.0))
                    .intentReason(text(root, "intentReason", "No intent reason provided."))
                    .rewrites(array(root, "rewrites"))
                    .semanticValidated(bool(root, "semanticValidated", false))
                    .semanticScore(decimal(root, "semanticScore", 0.0))
                    .semanticMeaning(text(root, "semanticMeaning", originalQuery))
                    .entities(array(root, "entities"))
                    .searchMode(enumValue(SearchMode.class, text(root, "searchMode", "KEYWORD"), SearchMode.KEYWORD))
                    .backend(enumValue(SearchBackend.class, text(root, "backend", "OPENSEARCH"), SearchBackend.OPENSEARCH))
                    .keywordSearch(bool(root, "keywordSearch", true))
                    .vectorSearch(bool(root, "vectorSearch", false))
                    .hybridSearch(bool(root, "hybridSearch", false))
                    .facets(bool(root, "facets", true))
                    .rerank(bool(root, "rerank", false))
                    .rewriteAgain(bool(root, "rewriteAgain", false))
                    .answer(bool(root, "answer", false))
                    .decisionConfidence(decimal(root, "decisionConfidence", 0.5))
                    .decisionReason(text(root, "decisionReason", "AI search analysis decision."))
                    .build();
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

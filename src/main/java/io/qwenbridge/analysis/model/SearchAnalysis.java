package io.qwenbridge.analysis.model;

import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchDecision;
import io.qwenbridge.decision.SearchMode;
import io.qwenbridge.intent.IntentAnalysis;
import io.qwenbridge.intent.IntentType;
import io.qwenbridge.pipeline.result.IntentResult;
import io.qwenbridge.pipeline.result.RewriteResult;
import io.qwenbridge.pipeline.result.SemanticResult;
import io.qwenbridge.semantic.SemanticAnalysis;
import io.qwenbridge.semantic.SemanticAmbiguity;
import io.qwenbridge.semantic.SemanticEntity;
import io.qwenbridge.semantic.SemanticEntityType;

import java.util.List;

public record SearchAnalysis(
        String language,
        IntentType intent,
        double intentConfidence,
        String intentReason,
        List<String> rewrites,
        boolean semanticValidated,
        double semanticScore,
        String semanticMeaning,
        List<String> entities,
        SearchMode searchMode,
        SearchBackend backend,
        boolean keywordSearch,
        boolean vectorSearch,
        boolean hybridSearch,
        boolean facets,
        boolean rerank,
        boolean rewriteAgain,
        boolean answer,
        double decisionConfidence,
        String decisionReason
) {
    public SearchAnalysis {
        language = blankToDefault(language, "unknown");
        intent = intent == null ? IntentType.UNKNOWN : intent;
        intentReason = blankToDefault(intentReason, "No intent reason provided.");
        rewrites = rewrites == null ? List.of() : List.copyOf(rewrites);
        semanticMeaning = blankToDefault(semanticMeaning, "No semantic meaning provided.");
        entities = entities == null ? List.of() : List.copyOf(entities);
        searchMode = searchMode == null ? SearchMode.KEYWORD : searchMode;
        backend = backend == null ? SearchBackend.OPENSEARCH : backend;
        decisionReason = blankToDefault(decisionReason, "No decision reason provided.");
        intentConfidence = clamp(intentConfidence);
        semanticScore = clamp(semanticScore);
        decisionConfidence = clamp(decisionConfidence);
    }

    public IntentResult toIntentResult() {
        return IntentResult.from(new IntentAnalysis(intent, intentReason, intentConfidence));
    }

    public RewriteResult toRewriteResult() {
        return new RewriteResult(!rewrites.isEmpty(), "qwen-analysis", rewrites);
    }

    public SemanticResult toSemanticResult(String originalQuery) {
        String normalizedQuery = rewrites.isEmpty() ? originalQuery.trim().toLowerCase() : rewrites.getFirst();

        SemanticAnalysis analysis = new SemanticAnalysis(
                originalQuery,
                normalizedQuery,
                semanticMeaning,
                entities.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(value -> new SemanticEntity(value, SemanticEntityType.UNKNOWN, semanticScore))
                        .toList(),
                List.of(),
                SemanticAmbiguity.none(),
                semanticScore
        );

        return new SemanticResult(semanticValidated, semanticScore, analysis);
    }

    public SearchDecision toSearchDecision() {
        return new SearchDecision(
                searchMode,
                backend,
                keywordSearch,
                vectorSearch,
                hybridSearch,
                facets,
                rerank,
                rewriteAgain,
                answer,
                decisionConfidence,
                decisionReason
        );
    }

    public static SearchAnalysis fallback(String query) {
        String normalized = query == null ? "" : query.trim();

        return new SearchAnalysis(
                "unknown",
                IntentType.UNKNOWN,
                0.20,
                "AI analysis failed; fallback analysis was used.",
                normalized.isBlank() ? List.of() : List.of(normalized),
                false,
                0.20,
                normalized.isBlank() ? "Unknown query." : normalized,
                List.of(),
                SearchMode.KEYWORD,
                SearchBackend.OPENSEARCH,
                true,
                false,
                false,
                true,
                false,
                false,
                false,
                0.50,
                "Fallback keyword search decision."
        );
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}

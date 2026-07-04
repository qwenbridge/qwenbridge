package io.qwenbridge.semantic;

import java.util.List;
import java.util.Objects;

public record SemanticAnalysis(
        String originalQuery,
        String normalizedQuery,
        String semanticMeaning,
        List<SemanticEntity> entities,
        List<String> domainHints,
        SemanticAmbiguity ambiguity,
        double confidence
) {

    public SemanticAnalysis {
        Objects.requireNonNull(originalQuery, "originalQuery must not be null");
        Objects.requireNonNull(normalizedQuery, "normalizedQuery must not be null");
        Objects.requireNonNull(semanticMeaning, "semanticMeaning must not be null");

        if (originalQuery.isBlank()) {
            throw new IllegalArgumentException("originalQuery must not be blank");
        }

        if (normalizedQuery.isBlank()) {
            throw new IllegalArgumentException("normalizedQuery must not be blank");
        }

        if (semanticMeaning.isBlank()) {
            throw new IllegalArgumentException("semanticMeaning must not be blank");
        }

        entities = entities == null ? List.of() : List.copyOf(entities);
        domainHints = domainHints == null ? List.of() : List.copyOf(domainHints);
        ambiguity = ambiguity == null ? SemanticAmbiguity.none() : ambiguity;

        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
    }

    public static SemanticAnalysis basic(String query) {
        return new SemanticAnalysis(
                query,
                query.trim().toLowerCase(),
                query.trim(),
                List.of(),
                List.of(),
                SemanticAmbiguity.none(),
                0.5
        );
    }
}

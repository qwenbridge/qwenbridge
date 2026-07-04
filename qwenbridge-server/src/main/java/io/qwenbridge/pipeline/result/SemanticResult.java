package io.qwenbridge.pipeline.result;

import io.qwenbridge.semantic.SemanticAnalysis;

public record SemanticResult(
        boolean validated,
        double score,
        SemanticAnalysis analysis
) {
    public SemanticResult(boolean validated, double score) {
        this(validated, score, SemanticAnalysis.basic("unknown"));
    }

    public static SemanticResult validated(SemanticAnalysis analysis) {
        return new SemanticResult(true, analysis.confidence(), analysis);
    }

    public static SemanticResult notValidated() {
        return new SemanticResult(false, 0.0, SemanticAnalysis.basic("unknown"));
    }
}

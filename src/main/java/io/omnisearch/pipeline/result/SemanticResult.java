package io.omnisearch.pipeline.result;

public record SemanticResult(
        boolean validated,
        double score
) {
    public static SemanticResult notValidated() {
        return new SemanticResult(false, 0.0);
    }
}

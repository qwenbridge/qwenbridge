package io.omnisearch.pipeline.result;

public record ConfidenceResult(double value) {
    public static ConfidenceResult zero() {
        return new ConfidenceResult(0.0);
    }
}

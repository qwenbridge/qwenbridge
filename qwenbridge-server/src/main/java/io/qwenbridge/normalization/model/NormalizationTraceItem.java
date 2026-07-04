package io.qwenbridge.normalization.model;

public record NormalizationTraceItem(
        String rule,
        String before,
        String after,
        boolean changed
) {
}

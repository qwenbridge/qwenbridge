package io.qwenbridge.pipeline.result;

import io.qwenbridge.intent.IntentAnalysis;

public record IntentResult(
        String intent,
        double confidence,
        String reason,
        IntentAnalysis analysis
) {
    public IntentResult(String intent) {
        this(intent, 0.0, "", null);
    }

    public static IntentResult from(IntentAnalysis analysis) {
        return new IntentResult(
                analysis.type().name(),
                analysis.confidence(),
                analysis.reason(),
                analysis
        );
    }

    public static IntentResult unknown() {
        return new IntentResult("UNKNOWN", 0.0, "Intent is unknown.", IntentAnalysis.unknown());
    }
}

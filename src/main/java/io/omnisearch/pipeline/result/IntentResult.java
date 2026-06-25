package io.omnisearch.pipeline.result;

public record IntentResult(String intent) {
    public static IntentResult unknown() {
        return new IntentResult("UNKNOWN");
    }
}

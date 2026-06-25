package io.omnisearch.pipeline.result;

public record LanguageResult(String language) {
    public static LanguageResult unknown() {
        return new LanguageResult("unknown");
    }
}

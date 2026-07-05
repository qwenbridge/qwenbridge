package io.qwenbridge.pipeline.result;

public record LanguageResult(String language) {
  public static LanguageResult unknown() {
    return new LanguageResult("unknown");
  }
}

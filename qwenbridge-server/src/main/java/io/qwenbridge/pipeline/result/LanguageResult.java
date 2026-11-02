package io.qwenbridge.pipeline.result;

import io.qwenbridge.language.LanguageAnalysis;
import java.util.Optional;

/**
 * Language step output.
 *
 * @param language legacy single code used by v1 responses, prompts and SSE: the detected (or
 *     primary) language, the declared language when nothing was detected, else {@code unknown}
 * @param analysis full typed analysis; absent only for results created by legacy code paths
 */
public record LanguageResult(String language, LanguageAnalysis analysis) {

  public LanguageResult(String language) {
    this(language, null);
  }

  public static LanguageResult unknown() {
    return new LanguageResult(LanguageAnalysis.UNKNOWN);
  }

  public Optional<LanguageAnalysis> analysisOptional() {
    return Optional.ofNullable(analysis);
  }
}

package io.qwenbridge.core.translation;

import io.qwenbridge.core.request.LanguageTag;
import java.time.Duration;
import java.util.Objects;

/**
 * One translation call (QB-015).
 *
 * @param text untrusted text to translate
 * @param source language of {@code text}: a determined tag, {@code mul} for code-switched text, or
 *     {@code und} when only a source-detecting provider may be asked
 * @param target determined target language
 * @param timeout time budget for the call
 */
public record TranslationRequest(
    String text, LanguageTag source, LanguageTag target, Duration timeout) {

  public TranslationRequest {
    Objects.requireNonNull(text, "text must not be null");
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(target, "target must not be null");
    Objects.requireNonNull(timeout, "timeout must not be null");
    if (target.isUndetermined()) {
      throw new IllegalArgumentException("target language must be determined");
    }
    if (timeout.isNegative() || timeout.isZero()) {
      throw new IllegalArgumentException("timeout must be positive");
    }
  }
}

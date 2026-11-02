package io.qwenbridge.pipeline.result;

import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.translation.TextRepresentations;
import io.qwenbridge.core.translation.TranslationOutcome;
import java.util.Objects;
import java.util.Optional;

/**
 * Translation step output (QB-016, QB-017).
 *
 * @param text original, normalized and (when accepted) translated text with their derivations
 * @param outcome what the optional translation stage decided
 * @param source source language given to the stage ({@code mul} for code-switched text)
 * @param target requested content language, when the client asked for one
 */
public record TranslationStepResult(
    TextRepresentations text,
    TranslationOutcome outcome,
    LanguageTag source,
    Optional<LanguageTag> target) {

  public TranslationStepResult {
    Objects.requireNonNull(text, "text must not be null");
    Objects.requireNonNull(outcome, "outcome must not be null");
    Objects.requireNonNull(source, "source must not be null");
    target = target == null ? Optional.empty() : target;
  }
}

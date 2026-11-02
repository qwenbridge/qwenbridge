package io.qwenbridge.core.translation;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * What the {@link TranslationStage} decided for one text (QB-016, QB-018).
 *
 * @param result the final result; {@code Failed(NOT_PRESERVED)} when an enforced preservation check
 *     rejected the provider's translation
 * @param preservation the preservation check, when a translation was produced
 * @param elapsed time spent, including the provider call
 */
public record TranslationOutcome(
    TranslationResult result, Optional<PreservationReport> preservation, Duration elapsed) {

  public TranslationOutcome {
    Objects.requireNonNull(result, "result must not be null");
    preservation = preservation == null ? Optional.empty() : preservation;
    Objects.requireNonNull(elapsed, "elapsed must not be null");
  }

  /** The translated text, only when the result is a usable translation. */
  public Optional<TranslationResult.Translated> translated() {
    return result instanceof TranslationResult.Translated t ? Optional.of(t) : Optional.empty();
  }
}

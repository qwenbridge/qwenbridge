package io.qwenbridge.core.translation;

import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.request.LanguageTag;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Typed result of a translation (QB-015, QB-016). Failures and skips are values, never exceptions,
 * so a missing or failing provider can never break the request.
 */
public sealed interface TranslationResult {

  /** A provider translated the text. */
  record Translated(
      String text,
      LanguageTag source,
      LanguageTag target,
      String providerId,
      Confidence confidence,
      Map<String, String> metadata)
      implements TranslationResult {

    public Translated {
      Objects.requireNonNull(text, "text must not be null");
      Objects.requireNonNull(source, "source must not be null");
      Objects.requireNonNull(target, "target must not be null");
      Objects.requireNonNull(providerId, "providerId must not be null");
      Objects.requireNonNull(confidence, "confidence must not be null");
      metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
      if (source.isUndetermined()) {
        throw new IllegalArgumentException("a translation must report a determined source");
      }
    }
  }

  /** Nothing was translated, deliberately. */
  record Skipped(SkipReason reason) implements TranslationResult {
    public Skipped {
      Objects.requireNonNull(reason, "reason must not be null");
    }
  }

  /** Translation was wanted but not produced. */
  record Failed(Failure failure, Optional<String> providerId) implements TranslationResult {
    public Failed {
      Objects.requireNonNull(failure, "failure must not be null");
      providerId = providerId == null ? Optional.empty() : providerId;
    }

    public static Failed of(Failure failure, String providerId) {
      return new Failed(failure, Optional.ofNullable(providerId));
    }
  }

  enum SkipReason {
    /** No target language was requested. */
    NOT_REQUESTED,
    /** The text is already in the target language. */
    SAME_LANGUAGE
  }

  enum Failure {
    /** No translation provider is configured. */
    NO_PROVIDER(true),
    /** The provider does not support this source or target language. */
    UNSUPPORTED_LANGUAGE_PAIR(true),
    /** The source language is unknown and the provider cannot detect it; nothing is guessed. */
    UNDETERMINED_SOURCE(true),
    /** The text exceeds the provider's input limit. */
    INPUT_TOO_LARGE(true),
    /** The provider did not answer within the time budget. */
    TIMEOUT(false),
    /** The provider failed. */
    PROVIDER_ERROR(false),
    /** The provider answered with something that is not a usable translation. */
    INVALID_OUTPUT(false),
    /** The translation changed numbers, units, entities, operators or negation (QB-018). */
    NOT_PRESERVED(false);

    private final boolean unsupported;

    Failure(boolean unsupported) {
      this.unsupported = unsupported;
    }

    /** Whether the request could not be served at all, as opposed to a failed attempt. */
    public boolean unsupported() {
      return unsupported;
    }
  }
}

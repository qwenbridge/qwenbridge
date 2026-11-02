package io.qwenbridge.core.request;

import java.util.Optional;

/**
 * Language information supplied by the client (QB-003). These are hints, never facts about the
 * text: {@code declared} is what the client says the input language is, {@code response} is the
 * language the client wants answers in, and {@code locale} carries regional conventions (number,
 * date and currency formatting). Detected language is a separate, later concern: see {@link
 * LanguageMetadata}.
 */
public record LanguagePreferences(
    Optional<LanguageTag> declared, Optional<LanguageTag> response, Optional<LanguageTag> locale) {

  public static final LanguagePreferences NONE =
      new LanguagePreferences(Optional.empty(), Optional.empty(), Optional.empty());

  public LanguagePreferences {
    declared = normalize(declared);
    response = normalize(response);
    locale = normalize(locale);
  }

  /** {@code und} carries no information, so it is treated the same as "not supplied". */
  private static Optional<LanguageTag> normalize(Optional<LanguageTag> tag) {
    return tag == null ? Optional.empty() : tag.filter(t -> !t.isUndetermined());
  }
}

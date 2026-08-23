package io.qwenbridge.input.model;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public record MultilingualInput(
    String originalText, String declaredLanguage, Locale locale, InputSource source) {

  public MultilingualInput {
    Objects.requireNonNull(originalText, "originalText must not be null");

    if (declaredLanguage != null && !declaredLanguage.isBlank()) {
      declaredLanguage = declaredLanguage.trim().toLowerCase(Locale.ROOT);
    }

    source = source == null ? InputSource.UNKNOWN : source;
  }

  public static MultilingualInput of(String originalText) {
    return new MultilingualInput(originalText, null, null, InputSource.UNKNOWN);
  }

  public static MultilingualInput of(
      String originalText, String declaredLanguage, Locale locale, InputSource source) {
    return new MultilingualInput(originalText, declaredLanguage, locale, source);
  }

  public Optional<String> declaredLanguageOptional() {
    if (declaredLanguage == null || declaredLanguage.isBlank()) {
      return Optional.empty();
    }

    return Optional.of(declaredLanguage);
  }

  public Optional<Locale> localeOptional() {
    return Optional.ofNullable(locale);
  }
}

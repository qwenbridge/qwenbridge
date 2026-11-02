package io.qwenbridge.core.request;

import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A validated BCP 47 language tag in canonical form (for example {@code fa}, {@code sv-SE}, {@code
 * zh-Hant-TW}).
 *
 * <p>Validation policy (QB-003):
 *
 * <ul>
 *   <li>The tag must be well-formed BCP 47 syntax (checked by {@link Locale.Builder}); underscores,
 *       empty subtags and malformed extensions are rejected.
 *   <li>A two-letter primary language must be a known ISO 639-1 code.
 *   <li>A three-letter primary language is accepted when well-formed (ISO 639-2/3 registry checks
 *       are not available in the JDK).
 *   <li>Four- to eight-letter primary language subtags are rejected: they are reserved or
 *       registered for exceptional use and are a common source of client mistakes (e.g. "english").
 *   <li>{@code und} (undetermined) is accepted and means "no language evidence".
 * </ul>
 */
public record LanguageTag(String value) {

  public static final int MAX_LENGTH = 64;

  // Must be initialized before any LanguageTag constant: the constructor validates against it.
  private static final Set<String> ISO_639_1 = Set.of(Locale.getISOLanguages());

  public static final LanguageTag UNDETERMINED = new LanguageTag("und");

  public LanguageTag {
    Objects.requireNonNull(value, "value must not be null");
    String canonical = canonicalize(value);
    if (canonical == null) {
      throw new IllegalArgumentException("Not a supported BCP 47 language tag");
    }
    value = canonical;
  }

  /** Parses an untrusted tag, returning empty for malformed or unsupported input. */
  public static Optional<LanguageTag> parse(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    String canonical = canonicalize(raw);
    return canonical == null ? Optional.empty() : Optional.of(new LanguageTag(canonical));
  }

  public static boolean isValid(String raw) {
    return raw != null && canonicalize(raw) != null;
  }

  public boolean isUndetermined() {
    return "und".equals(primaryLanguage());
  }

  /** The primary language subtag, e.g. {@code fa} for {@code fa-IR}. */
  public String primaryLanguage() {
    int dash = value.indexOf('-');
    return dash < 0 ? value : value.substring(0, dash);
  }

  public Locale toLocale() {
    return Locale.forLanguageTag(value);
  }

  @Override
  public String toString() {
    return value;
  }

  private static String canonicalize(String raw) {
    String trimmed = raw.trim();
    if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) {
      return null;
    }
    Locale locale;
    try {
      locale = new Locale.Builder().setLanguageTag(trimmed).build();
    } catch (IllformedLocaleException e) {
      return null;
    }
    String language = locale.getLanguage();
    if (language.isEmpty()) {
      // The JDK represents "und" as an empty language; any other empty language (e.g. a
      // private-use-only tag like "x-foo") carries no primary language and is rejected.
      String firstSubtag = trimmed.split("-", 2)[0];
      return "und".equalsIgnoreCase(firstSubtag) ? locale.toLanguageTag() : null;
    }
    if (language.length() == 2 && !ISO_639_1.contains(language)) {
      return null;
    }
    if (language.length() > 3) {
      return null;
    }
    return locale.toLanguageTag();
  }
}

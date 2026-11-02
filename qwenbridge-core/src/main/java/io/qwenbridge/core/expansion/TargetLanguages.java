package io.qwenbridge.core.expansion;

import io.qwenbridge.core.expansion.TargetLanguage.Resolution;
import io.qwenbridge.core.request.LanguageTag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Validates and normalizes configured target languages (QB-286). The Core holds no language list:
 * identifiers are checked only against BCP 47 syntax and the JDK's ISO 639 and ISO 3166 code lists.
 *
 * <ul>
 *   <li>Case-insensitive: {@code EN}, {@code FA}, {@code NB} and {@code NN} become {@code en},
 *       {@code fa}, {@code nb} and {@code nn}.
 *   <li>A two-letter identifier written in upper case that is also an ISO 3166 region code is not
 *       guessed: {@code NO} (Norwegian or Norway?) is reported {@code AMBIGUOUS_REGION_CODE}; a
 *       region code that is no language at all ({@code DK}, {@code US}) is {@code INVALID}.
 *       Lower-case codes are language codes.
 *   <li>Compatibility aliases apply only when configured, matched exactly and case-sensitively (for
 *       example {@code SE} to {@code sv}: {@code SE} is the region Sweden, while {@code se} is the
 *       language code of Northern Sami).
 *   <li>Duplicates (after normalization) are reported and get no second result; the configured
 *       order is preserved.
 * </ul>
 */
public final class TargetLanguages {

  private static final Set<String> REGIONS = Set.of(Locale.getISOCountries());
  private static final Set<String> NOT_TARGETS = Set.of("und", "mul", "zxx", "mis");

  private TargetLanguages() {}

  public static List<TargetLanguage> resolve(List<String> configured, Map<String, String> aliases) {
    List<TargetLanguage> out = new ArrayList<>();
    Map<String, String> seen = new LinkedHashMap<>();
    Map<String, String> alias = aliases == null ? Map.of() : aliases;
    for (String raw : configured == null ? List.<String>of() : configured) {
      TargetLanguage entry = one(raw == null ? "" : raw, alias);
      if (entry.usable()) {
        String key = entry.language().orElseThrow().value();
        String first = seen.putIfAbsent(key, entry.configured());
        if (first != null)
          entry =
              new TargetLanguage(
                  entry.configured(),
                  entry.language(),
                  Resolution.DUPLICATE,
                  (entry.resolution() == Resolution.ALIAS ? entry.note() + "; " : "")
                      + "duplicate of "
                      + first
                      + " ("
                      + key
                      + ")");
      }
      out.add(entry);
    }
    return List.copyOf(out);
  }

  /** The distinct usable target languages, in configured order. */
  public static List<LanguageTag> usable(List<TargetLanguage> resolved) {
    return resolved.stream()
        .filter(TargetLanguage::usable)
        .map(t -> t.language().orElseThrow())
        .toList();
  }

  private static TargetLanguage one(String raw, Map<String, String> aliases) {
    String value = raw.strip();
    if (value.isEmpty())
      return new TargetLanguage(raw, Optional.empty(), Resolution.INVALID, "empty identifier");
    if (aliases.containsKey(value)) {
      Optional<LanguageTag> target = LanguageTag.parse(aliases.get(value));
      if (target.isEmpty() || NOT_TARGETS.contains(target.get().primaryLanguage()))
        return new TargetLanguage(
            raw, Optional.empty(), Resolution.INVALID, "alias target is not a language tag");
      return new TargetLanguage(
          raw,
          target,
          Resolution.ALIAS,
          "compatibility alias " + value + " -> " + target.get().value());
    }
    if (value.length() == 2
        && value.chars().allMatch(c -> c >= 'A' && c <= 'Z')
        && REGIONS.contains(value)) {
      boolean alsoLanguage = LanguageTag.parse(value).isPresent();
      return alsoLanguage
          ? new TargetLanguage(
              raw,
              Optional.empty(),
              Resolution.AMBIGUOUS_REGION_CODE,
              value
                  + " is both a region code and the language code "
                  + value.toLowerCase(Locale.ROOT)
                  + "; write language codes in lower case")
          : new TargetLanguage(
              raw,
              Optional.empty(),
              Resolution.INVALID,
              value + " is a region code, not a language code");
    }
    Optional<LanguageTag> tag = LanguageTag.parse(value);
    if (tag.isEmpty())
      return new TargetLanguage(
          raw, Optional.empty(), Resolution.INVALID, "not a valid BCP 47 language tag");
    if (NOT_TARGETS.contains(tag.get().primaryLanguage()))
      return new TargetLanguage(
          raw, Optional.empty(), Resolution.NOT_A_TARGET, "not a target language");
    return tag.get().value().equals(value)
        ? new TargetLanguage(raw, tag, Resolution.VALID, "valid")
        : new TargetLanguage(
            raw, tag, Resolution.NORMALIZED, "normalized " + value + " -> " + tag.get().value());
  }
}

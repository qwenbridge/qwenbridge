package io.qwenbridge.core.extraction;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Country names in English, Swedish and Persian taken from the JDK's CLDR data (QB-031), mapped to
 * ISO 3166-1 alpha-2 codes. Names that are also common English words ("Turkey", "China", "Jordan",
 * "Chad") are excluded to avoid false geographic constraints; cities are not resolved at all,
 * because their ambiguity cannot be settled without context.
 */
final class Gazetteer {

  private static final Set<String> COMMON_WORDS =
      Set.of("turkey", "china", "jordan", "chad", "georgia", "guinea", "niger", "jersey", "chile");

  private static final Map<String, String> NAMES = load();

  static final Pattern PATTERN = compile();

  private Gazetteer() {}

  private static Map<String, String> load() {
    Map<String, String> names = new HashMap<>();
    for (String code : Locale.getISOCountries()) {
      Locale country = new Locale.Builder().setRegion(code).build();
      for (String language : List.of("en", "sv", "fa")) {
        String name = country.getDisplayCountry(Locale.forLanguageTag(language));
        String key = name.toLowerCase(Locale.ROOT);
        if (name.length() >= 4 && !name.equals(code) && !COMMON_WORDS.contains(key)) {
          names.putIfAbsent(key, code);
        }
      }
    }
    return Map.copyOf(names);
  }

  private static Pattern compile() {
    String alternation =
        NAMES.keySet().stream()
            .sorted(
                Comparator.comparingInt(String::length)
                    .reversed()
                    .thenComparing(Comparator.naturalOrder()))
            .map(Pattern::quote)
            .collect(Collectors.joining("|"));
    return Pattern.compile(
        "(?<![\\p{L}\\p{M}\\u200c])(?:" + alternation + ")(?![\\p{L}\\p{M}\\u200c])",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  }

  static Optional<String> code(String name) {
    return Optional.ofNullable(NAMES.get(name.toLowerCase(Locale.ROOT)));
  }
}

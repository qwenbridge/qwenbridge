package io.qwenbridge.language;

import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class LanguageService {

  private static final String UNKNOWN = "unknown";
  private static final String ENGLISH = "en";

  private static final int SHORT_LATIN_QUERY_MAX_WORDS = 4;

  private static final Pattern JAPANESE_KANA = Pattern.compile(".*[\\u3040-\\u30FF].*");

  private static final Pattern PERSIAN_SPECIFIC_LETTERS =
      Pattern.compile(".*[\\u067E\\u0686\\u0698\\u06AF\\u06A9\\u06CC].*");

  private static final Pattern LATIN_LETTERS = Pattern.compile(".*\\p{IsLatin}.*");

  private final LanguageDetector detector =
      LanguageDetectorBuilder.fromAllLanguages()
          .withLowAccuracyMode()
          .withMinimumRelativeDistance(0.05)
          .build();

  public String detect(String query) {
    if (query == null || query.isBlank()) {
      return UNKNOWN;
    }

    String normalized = query.trim();

    String scriptLanguage = detectByHighConfidenceScript(normalized);
    if (!UNKNOWN.equals(scriptLanguage)) {
      return scriptLanguage;
    }

    if (isShortLatinQuery(normalized)) {
      return ENGLISH;
    }

    String modelLanguage = detectByModel(normalized);
    if (!UNKNOWN.equals(modelLanguage)) {
      return modelLanguage;
    }

    if (containsLatinLetters(normalized)) {
      return ENGLISH;
    }

    return UNKNOWN;
  }

  private String detectByModel(String query) {
    Language language = detector.detectLanguageOf(query);

    if (language == Language.UNKNOWN) {
      return UNKNOWN;
    }

    return language.getIsoCode639_1().toString().toLowerCase(Locale.ROOT);
  }

  private String detectByHighConfidenceScript(String query) {
    if (JAPANESE_KANA.matcher(query).matches()) {
      return "ja";
    }

    if (PERSIAN_SPECIFIC_LETTERS.matcher(query).matches()) {
      return "fa";
    }

    return UNKNOWN;
  }

  private boolean containsLatinLetters(String query) {
    return LATIN_LETTERS.matcher(query).matches();
  }

  private boolean isShortLatinQuery(String query) {
    if (!containsLatinLetters(query)) {
      return false;
    }

    String normalized = query.replaceAll("[^\\p{IsLatin}\\p{IsDigit}]+", " ").trim();

    if (normalized.isBlank()) {
      return false;
    }

    return normalized.split("\\s+").length <= SHORT_LATIN_QUERY_MAX_WORDS;
  }
}

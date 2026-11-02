package io.qwenbridge.core.language;

import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.request.LanguageTag;
import java.lang.Character.UnicodeScript;
import java.util.EnumMap;
import java.util.Map;

/**
 * Deterministic, dependency-free fallback detector (QB-008). It only answers when the writing
 * system itself identifies the language; otherwise it returns {@code und}:
 *
 * <ul>
 *   <li>Scripts used by essentially one language (Hangul, kana, Hebrew, Thai, Greek, Armenian,
 *       Georgian) map to that language.
 *   <li>Arabic script maps to Urdu or Persian only when language-specific letters are present;
 *       plain Arabic script stays {@code und} because it is shared by several languages.
 *   <li>Latin, Cyrillic, Han-only and other shared scripts stay {@code und}: a Latin-script word is
 *       not evidence of English.
 * </ul>
 *
 * A script decides only when it accounts for at least 60% of the letters.
 */
public final class ScriptHeuristicLanguageDetector implements LanguageDetector {

  public static final String ID = "script-heuristic-v1";

  private static final double DOMINANCE = 0.6;
  private static final String PERSIAN_LETTERS = "پچژگکی";
  private static final String URDU_LETTERS = "ٹڈڑںھہے";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public LanguageDetection detect(String text) {
    if (text == null) {
      return LanguageDetection.undetermined(ID);
    }
    Map<UnicodeScript, Integer> letters = new EnumMap<>(UnicodeScript.class);
    int total = 0;
    boolean kana = false;
    boolean persian = false;
    boolean urdu = false;
    for (int i = 0; i < text.length(); ) {
      int cp = text.codePointAt(i);
      i += Character.charCount(cp);
      if (!Character.isLetter(cp)) {
        continue;
      }
      UnicodeScript script = UnicodeScript.of(cp);
      if (script == UnicodeScript.HIRAGANA || script == UnicodeScript.KATAKANA) {
        kana = true;
        script = UnicodeScript.HAN; // Japanese text mixes kana and kanji.
      }
      letters.merge(script, 1, Integer::sum);
      total++;
      persian |= PERSIAN_LETTERS.indexOf(cp) >= 0;
      urdu |= URDU_LETTERS.indexOf(cp) >= 0;
    }
    if (total == 0) {
      return LanguageDetection.undetermined(ID);
    }
    UnicodeScript dominant = null;
    for (Map.Entry<UnicodeScript, Integer> entry : letters.entrySet()) {
      if (entry.getValue() >= DOMINANCE * total) {
        dominant = entry.getKey();
      }
    }
    if (dominant == null) {
      return LanguageDetection.undetermined(ID);
    }
    String language =
        switch (dominant) {
          case HANGUL -> "ko";
          case HAN -> kana ? "ja" : null;
          case HEBREW -> "he";
          case THAI -> "th";
          case GREEK -> "el";
          case ARMENIAN -> "hy";
          case GEORGIAN -> "ka";
          case ARABIC -> urdu ? "ur" : persian ? "fa" : null;
          default -> null;
        };
    if (language == null) {
      return LanguageDetection.undetermined(ID);
    }
    return new LanguageDetection(
        LanguageTag.parse(language).orElseThrow(), Confidence.heuristic(), ID);
  }
}

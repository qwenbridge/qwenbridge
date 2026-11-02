package io.qwenbridge.threat.detector.unicode;

import io.qwenbridge.threat.detector.support.PatternBasedThreatDetector;
import io.qwenbridge.threat.model.ThreatFinding;
import io.qwenbridge.threat.model.ThreatType;
import io.qwenbridge.threat.rule.ThreatRuleLoader;
import jakarta.inject.Singleton;
import java.lang.Character.UnicodeScript;
import java.util.List;
import java.util.Set;

/**
 * Detects invisible-character obfuscation. Before the pattern rules run, characters that are
 * required by legitimate orthography are removed from the inspected copy (QB-011, QB-070):
 *
 * <ul>
 *   <li>ZWNJ and ZWJ between letters (or marks) of joining scripts — Persian, Urdu, Arabic, Indic
 *       and similar — e.g. the Persian half-space in "می‌خوام";
 *   <li>ZWJ inside emoji sequences (pictograph, skin tone or variation selector before it and a
 *       pictograph after it);
 *   <li>tag characters inside an emoji subdivision flag (U+1F3F4 followed by tags and a cancel
 *       tag).
 * </ul>
 *
 * Any joiner or tag character that remains is treated as obfuscation by the YAML rules. The checks
 * work on code points, so supplementary characters (emoji) are handled correctly. The original
 * input is never modified.
 */
@Singleton
public class UnicodeObfuscationDetector extends PatternBasedThreatDetector {

  private static final int ZWNJ = 0x200C;
  private static final int ZWJ = 0x200D;
  private static final int VS16 = 0xFE0F;
  private static final int BLACK_FLAG = 0x1F3F4;
  private static final int CANCEL_TAG = 0xE007F;

  /** Scripts that do not use joiners in their orthography. */
  private static final Set<UnicodeScript> NON_JOINING =
      Set.of(
          UnicodeScript.LATIN,
          UnicodeScript.CYRILLIC,
          UnicodeScript.GREEK,
          UnicodeScript.HAN,
          UnicodeScript.HANGUL,
          UnicodeScript.HIRAGANA,
          UnicodeScript.KATAKANA,
          UnicodeScript.COMMON);

  public UnicodeObfuscationDetector(ThreatRuleLoader ruleLoader) {
    super(ruleLoader.load("threat-rules/unicode-obfuscation.yml", ThreatType.UNICODE_OBFUSCATION));
  }

  @Override
  public List<ThreatFinding> detect(String input) {
    return input == null ? List.of() : super.detect(withoutLegitimateInvisibles(input));
  }

  /** Copy of {@code text} without joiners and tags that legitimate orthography requires. */
  static String withoutLegitimateInvisibles(String text) {
    int[] cps = text.codePoints().toArray();
    StringBuilder out = new StringBuilder(text.length());
    for (int i = 0; i < cps.length; i++) {
      int cp = cps[i];
      if ((cp == ZWNJ || cp == ZWJ) && legitimateJoiner(cps, i)) {
        continue;
      }
      if (cp == BLACK_FLAG && flagSequenceEnd(cps, i) > i) {
        out.appendCodePoint(cp);
        i = flagSequenceEnd(cps, i); // skip the tag letters and the cancel tag
        continue;
      }
      out.appendCodePoint(cp);
    }
    return out.toString();
  }

  private static boolean legitimateJoiner(int[] cps, int i) {
    if (i == 0 || i == cps.length - 1) {
      return false;
    }
    int before = cps[i - 1];
    int after = cps[i + 1];
    boolean joiningScript = joiningLetterOrMark(before) && joiningLetter(after);
    boolean emoji =
        cps[i] == ZWJ
            && (pictographic(before) || emojiModifier(before) || before == VS16)
            && pictographic(after);
    return joiningScript || emoji;
  }

  /** Index of the cancel tag closing a subdivision flag that starts at {@code i}, or -1. */
  private static int flagSequenceEnd(int[] cps, int i) {
    int j = i + 1;
    while (j < cps.length && cps[j] >= 0xE0061 && cps[j] <= 0xE007A && j - i <= 6) {
      j++;
    }
    return j > i + 2 && j < cps.length && cps[j] == CANCEL_TAG ? j : -1;
  }

  private static boolean joiningLetter(int cp) {
    return Character.isLetter(cp) && !NON_JOINING.contains(UnicodeScript.of(cp));
  }

  private static boolean joiningLetterOrMark(int cp) {
    int type = Character.getType(cp);
    boolean mark = type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK;
    return (Character.isLetter(cp) || mark) && !NON_JOINING.contains(UnicodeScript.of(cp));
  }

  private static boolean pictographic(int cp) {
    return Character.isExtendedPictographic(cp);
  }

  private static boolean emojiModifier(int cp) {
    return Character.isEmojiModifier(cp);
  }

  @Override
  public String name() {
    return "unicode-obfuscation-detector";
  }

  @Override
  public ThreatType type() {
    return ThreatType.UNICODE_OBFUSCATION;
  }

  @Override
  public int order() {
    return 120;
  }
}

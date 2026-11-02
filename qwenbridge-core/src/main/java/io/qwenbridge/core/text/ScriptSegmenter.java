package io.qwenbridge.core.text;

import java.lang.Character.UnicodeScript;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Splits text into script runs (QB-013). Script detection is not language identification: a
 * Latin-script segment may be English, Swedish or a brand name. Language is assigned per segment by
 * a {@link io.qwenbridge.core.language.LanguageDetector}, which may answer {@code und}.
 */
public final class ScriptSegmenter {

  private ScriptSegmenter() {}

  public static List<ScriptSegment> segment(String text) {
    if (text == null || text.isEmpty()) {
      return List.of();
    }
    List<ScriptSegment> segments = new ArrayList<>();
    int start = 0;
    UnicodeScript current = null;
    for (int i = 0; i < text.length(); ) {
      int cp = text.codePointAt(i);
      UnicodeScript script = letterScript(cp);
      if (script != null) {
        if (current == null) {
          current = script; // Leading neutral characters join the first lettered segment.
        } else if (script != current) {
          // A new segment starts at its first letter; neutral text before it (spaces,
          // punctuation, digits) stays with the preceding segment.
          segments.add(new ScriptSegment(start, i, current, text.substring(start, i)));
          start = i;
          current = script;
        }
      }
      i += Character.charCount(cp);
    }
    UnicodeScript last = current == null ? UnicodeScript.COMMON : current;
    segments.add(new ScriptSegment(start, text.length(), last, text.substring(start)));
    return List.copyOf(segments);
  }

  /** Distinct letter scripts, in order of first appearance. */
  public static Set<UnicodeScript> scripts(String text) {
    Set<UnicodeScript> scripts = new LinkedHashSet<>();
    for (ScriptSegment segment : segment(text)) {
      if (segment.script() != UnicodeScript.COMMON) {
        scripts.add(segment.script());
      }
    }
    return scripts;
  }

  /**
   * Script that decides segmentation, or null for neutral characters. Kana are folded into Han
   * because Japanese mixes them within words.
   */
  private static UnicodeScript letterScript(int cp) {
    if (!Character.isLetter(cp)) {
      return null;
    }
    UnicodeScript script = UnicodeScript.of(cp);
    if (script == UnicodeScript.COMMON || script == UnicodeScript.INHERITED) {
      return null;
    }
    if (script == UnicodeScript.HIRAGANA || script == UnicodeScript.KATAKANA) {
      return UnicodeScript.HAN;
    }
    return script;
  }
}

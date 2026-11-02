package io.qwenbridge.core.request;

/**
 * Dominant writing direction of a text, derived from Unicode bidirectional character classes
 * (QB-004). This describes the text; it does not change it.
 */
public enum TextDirection {
  /** Only left-to-right strong characters (e.g. Latin, Cyrillic, CJK). */
  LTR,
  /** Only right-to-left strong characters (Arabic-script or Hebrew-script letters). */
  RTL,
  /** Both left-to-right and right-to-left strong characters, e.g. code-switched input. */
  MIXED,
  /** No strong directional characters (digits, punctuation, symbols, emoji only). */
  NEUTRAL;

  public static TextDirection of(CharSequence text) {
    if (text == null) {
      return NEUTRAL;
    }
    boolean ltr = false;
    boolean rtl = false;
    for (int i = 0; i < text.length(); ) {
      int codePoint = Character.codePointAt(text, i);
      switch (Character.getDirectionality(codePoint)) {
        case Character.DIRECTIONALITY_LEFT_TO_RIGHT -> ltr = true;
        case Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC ->
            rtl = true;
        default -> {
          // Weak, neutral and explicit formatting characters do not decide direction.
        }
      }
      if (ltr && rtl) {
        return MIXED;
      }
      i += Character.charCount(codePoint);
    }
    if (rtl) {
      return RTL;
    }
    return ltr ? LTR : NEUTRAL;
  }
}

package io.qwenbridge.core.extraction;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Exact number parsing for any decimal digit script (QB-030): ASCII, Persian ({@code ۰-۹}),
 * Arabic-Indic, Devanagari, full-width. Values are {@link BigDecimal}; floating point is never
 * used.
 *
 * <p>Separators: the Persian decimal ({@code ٫}) and thousands ({@code ٬}) signs are unambiguous.
 * For {@code .} and {@code ,} the language decides: Swedish (and other comma-decimal languages)
 * read {@code ,} as decimal; otherwise {@code .} is decimal. A single separator followed by exactly
 * three digits is read as a thousands separator in the opposite convention, and the result is
 * reported as {@link Parsed#ambiguous()}.
 */
public final class Numerals {

  /** A number token: digits with optional internal separators. */
  // Possessive: digits and separators are disjoint, so nothing is given back, and a long run of
  // groups cannot recurse the regex engine into a StackOverflowError.
  public static final String TOKEN = "\\p{Nd}++(?:[.,\\u066B\\u066C]\\p{Nd}++)*+";

  static final Pattern TOKEN_PATTERN = Pattern.compile(TOKEN);

  private Numerals() {}

  /** Languages that write {@code ,} as the decimal mark. */
  private static final java.util.Set<String> COMMA_DECIMAL =
      java.util.Set.of(
          "sv", "de", "fr", "es", "it", "nl", "pt", "da", "nb", "nn", "no", "fi", "tr", "ru", "pl");

  /** Whether {@code ,} is the decimal mark for this language (unknown language: no). */
  public static boolean commaDecimal(
      java.util.Optional<io.qwenbridge.core.request.LanguageTag> language) {
    return language.map(l -> COMMA_DECIMAL.contains(l.primaryLanguage())).orElse(false);
  }

  /**
   * A parsed value; {@code ambiguous} when the separator interpretation was a convention choice.
   */
  public record Parsed(BigDecimal value, boolean ambiguous) {}

  public static Optional<Parsed> parse(String token, boolean commaDecimal) {
    if (token == null || !TOKEN_PATTERN.matcher(token).matches()) {
      return Optional.empty();
    }
    StringBuilder ascii = new StringBuilder(token.length());
    int separators = 0;
    int lastSeparator = -1;
    char lastSeparatorChar = 0;
    for (int i = 0; i < token.length(); ) {
      int cp = token.codePointAt(i);
      i += Character.charCount(cp);
      int digit = Character.digit(cp, 10);
      if (digit >= 0) {
        ascii.append((char) ('0' + digit));
      } else {
        separators++;
        lastSeparator = ascii.length();
        lastSeparatorChar = (char) cp;
        ascii.append((char) cp);
      }
    }
    String raw = ascii.toString();
    if (separators == 0) {
      return Optional.of(new Parsed(new BigDecimal(raw), false));
    }
    if (raw.indexOf('٫') >= 0 || raw.indexOf('٬') >= 0) {
      return decimal(raw.replace("٬", "").replace('٫', '.'), false);
    }
    char decimalMark = commaDecimal ? ',' : '.';
    char groupMark = commaDecimal ? '.' : ',';
    int digitsAfterLast = raw.length() - lastSeparator - 1;
    boolean ambiguous = false;
    String normalized;
    if (separators == 1 && lastSeparatorChar == decimalMark && digitsAfterLast == 3) {
      // "1.200" (en) or "1,200" (sv): could be a thousands group in the other convention.
      normalized = raw.replace(decimalMark, '.');
      ambiguous = true;
    } else if (lastSeparatorChar == decimalMark) {
      normalized =
          raw.substring(0, lastSeparator).replace(String.valueOf(groupMark), "")
              + "."
              + raw.substring(lastSeparator + 1);
      if (normalized.indexOf(decimalMark) >= 0 && decimalMark != '.') {
        return Optional.empty();
      }
    } else {
      // Only group separators: every group must have exactly three digits.
      String[] groups = raw.split(Pattern.quote(String.valueOf(groupMark)), -1);
      for (int g = 1; g < groups.length; g++) {
        if (groups[g].length() != 3) {
          return Optional.empty();
        }
      }
      if (raw.indexOf(decimalMark) >= 0) {
        return Optional.empty();
      }
      normalized = raw.replace(String.valueOf(groupMark), "");
    }
    return decimal(normalized, ambiguous);
  }

  private static Optional<Parsed> decimal(String text, boolean ambiguous) {
    try {
      return Optional.of(new Parsed(new BigDecimal(text), ambiguous));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }
}

package io.qwenbridge.core.spelling;

import io.qwenbridge.core.text.Identifiers;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits text into word tokens and marks the ones that must never be corrected (QB-291 E): URLs,
 * e-mail addresses, IP addresses, UUIDs, host names, technical and product identifiers, numbers and
 * currency amounts, quoted values, upper-case acronyms (such as currency codes), words joined by
 * hyphens, underscores or dots, words shorter than three characters, and mixed-case names. Offsets
 * are UTF-16 indices into the text. Letters of any script count, including joiners (ZWNJ) and
 * combining marks inside a word.
 */
public final class ProtectedTokens {

  public enum Kind {
    WORD,
    PROTECTED
  }

  public record Token(String text, int start, int end, Kind kind, String reason) {}

  private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?|ftp)://\\S+|\\bwww\\.\\S+");
  private static final Pattern EMAIL =
      Pattern.compile("[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}.-]+\\.\\p{L}{2,}");
  private static final Pattern HOST = Pattern.compile("[\\p{L}\\p{N}-]+(?:\\.[\\p{L}\\p{N}-]+)+");
  private static final Pattern AMOUNT =
      Pattern.compile("\\p{Sc}\\s?\\p{N}[\\p{N}.,\\s]*|\\p{N}[\\p{N}.,]*\\s?\\p{Sc}");
  private static final Pattern WORD =
      Pattern.compile("[\\p{L}\\p{M}\\u200C\\u200D]+(?:['’][\\p{L}\\p{M}]+)*");

  /** Any run joined by hyphens, underscores or dots, or containing digits. */
  private static final Pattern JOINED =
      Pattern.compile("[\\p{L}\\p{N}\\p{M}]+(?:[-_.][\\p{L}\\p{N}\\p{M}]+)+");

  private static final Pattern WITH_DIGITS =
      Pattern.compile("[\\p{L}\\p{M}]*\\p{N}[\\p{L}\\p{N}\\p{M}]*");

  private ProtectedTokens() {}

  private record Span(int start, int end, String reason) {}

  public static List<Token> tokenize(String text) {
    List<Span> protectedSpans = new ArrayList<>();
    mark(protectedSpans, URL.matcher(text), "url");
    mark(protectedSpans, EMAIL.matcher(text), "email");
    mark(protectedSpans, Identifiers.IPV4.matcher(text), "ip-address");
    mark(protectedSpans, Identifiers.UUID.matcher(text), "uuid");
    mark(protectedSpans, Identifiers.QUOTED.matcher(text), "quoted");
    mark(protectedSpans, HOST.matcher(text), "hostname");
    mark(protectedSpans, Identifiers.CODE.matcher(text), "identifier");
    mark(protectedSpans, JOINED.matcher(text), "joined-identifier");
    mark(protectedSpans, AMOUNT.matcher(text), "amount");
    mark(protectedSpans, WITH_DIGITS.matcher(text), "number");
    List<Token> tokens = new ArrayList<>();
    Matcher m = WORD.matcher(text);
    while (m.find()) {
      String word = m.group();
      String reason = null;
      for (Span s : protectedSpans)
        if (m.start() < s.end() && s.start() < m.end()) {
          reason = s.reason();
          break;
        }
      if (reason == null) reason = intrinsic(word);
      tokens.add(
          new Token(word, m.start(), m.end(), reason == null ? Kind.WORD : Kind.PROTECTED, reason));
    }
    return tokens;
  }

  /**
   * Text that a translation must keep exactly (QB-286): URLs, e-mail addresses, IP addresses,
   * UUIDs, host names, codes, joined technical identifiers (with a digit, underscore or dot, such
   * as {@code redis03-pt1}) and the content of quoted values. Numbers, amounts and units are not
   * listed: they may legitimately change digit script and are checked by the translation
   * preservation check instead. Spans inside a longer span are dropped; order is preserved.
   */
  public static List<String> verbatim(String text) {
    List<Span> spans = new ArrayList<>();
    mark(spans, URL.matcher(text), "url");
    mark(spans, EMAIL.matcher(text), "email");
    mark(spans, Identifiers.IPV4.matcher(text), "ip-address");
    mark(spans, Identifiers.UUID.matcher(text), "uuid");
    Matcher quoted = Identifiers.QUOTED.matcher(text);
    while (quoted.find())
      for (int g = 1; g <= quoted.groupCount(); g++)
        if (quoted.group(g) != null && !quoted.group(g).isBlank())
          spans.add(new Span(quoted.start(g), quoted.end(g), "quoted"));
    mark(spans, HOST.matcher(text), "hostname");
    mark(spans, Identifiers.CODE.matcher(text), "identifier");
    Matcher joined = JOINED.matcher(text);
    while (joined.find())
      if (joined.group().chars().anyMatch(c -> Character.isDigit(c) || c == '_' || c == '.'))
        spans.add(new Span(joined.start(), joined.end(), "joined-identifier"));
    List<Span> kept = new ArrayList<>();
    for (Span s : spans) {
      boolean inside =
          spans.stream()
              .anyMatch(
                  o ->
                      o != s
                          && o.start() <= s.start()
                          && s.end() <= o.end()
                          && (o.end() - o.start()) > (s.end() - s.start()));
      boolean duplicate = kept.stream().anyMatch(k -> k.start() == s.start() && k.end() == s.end());
      if (!inside && !duplicate) kept.add(s);
    }
    kept.sort(java.util.Comparator.comparingInt(Span::start));
    return kept.stream().map(s -> text.substring(s.start(), s.end())).toList();
  }

  private static void mark(List<Span> spans, Matcher m, String reason) {
    while (m.find()) spans.add(new Span(m.start(), m.end(), reason));
  }

  private static String intrinsic(String word) {
    int[] cps = word.codePoints().filter(Character::isLetter).toArray();
    if (cps.length < 3) return "short";
    boolean hasCase = false;
    boolean allUpper = true;
    boolean innerUpper = false;
    for (int i = 0; i < cps.length; i++) {
      if (Character.isUpperCase(cps[i]) || Character.isLowerCase(cps[i])) hasCase = true;
      if (!Character.isUpperCase(cps[i])) allUpper = false;
      if (i > 0 && Character.isUpperCase(cps[i])) innerUpper = true;
    }
    if (hasCase && allUpper) return "acronym";
    if (innerUpper) return "mixed-case-name";
    return null;
  }
}

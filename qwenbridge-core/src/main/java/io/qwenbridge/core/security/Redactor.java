package io.qwenbridge.core.security;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes secrets from text before it reaches logs, traces or error messages (QB-061). It is a last
 * line of defence: code must not log secrets in the first place, but any user text, upstream error
 * or URL that does carry one is masked here.
 *
 * <p>Masked: bearer and basic credentials, JSON Web Tokens, well-known API token formats (OpenAI-,
 * GitHub-, Slack-, AWS- and Google-style keys), {@code key=value} pairs whose key names a secret,
 * user information in URLs, PEM private keys and payment card numbers (Luhn-checked). The key or
 * scheme is kept so the log stays useful; only the value becomes {@value #MASK}.
 */
public final class Redactor {

  public static final String MASK = "[REDACTED]";

  private static final Pattern PEM =
      Pattern.compile(
          "-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?(?:-----END [A-Z ]*PRIVATE KEY-----|$)");

  private static final Pattern AUTH_SCHEME =
      Pattern.compile("(?i)\\b(bearer|basic)(\\s+)[A-Za-z0-9._~+/=-]{8,}");

  private static final Pattern JWT =
      Pattern.compile("\\beyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}");

  private static final Pattern TOKEN_FORMATS =
      Pattern.compile(
          "\\b(?:sk-[A-Za-z0-9_-]{16,}|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}"
              + "|xox[abprs]-[A-Za-z0-9-]{10,}|AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{35})");

  private static final Pattern URL_USERINFO =
      // The scheme is bounded (real schemes are short) so the scan is linear on long dotted runs.
      Pattern.compile("(?i)\\b([a-z][a-z0-9+.-]{0,63}://)[^/\\s:@]++:[^/\\s@]++@");

  private static final Pattern SECRET_PAIR =
      Pattern.compile(
          "(?i)(\"?\\b(?:password|passwd|pwd|secret|client[_-]?secret|token|access[_-]?token"
              + "|refresh[_-]?token|api[_-]?key|apikey|access[_-]?key|private[_-]?key"
              + "|authorization|x-api-key)\\b\"?)(\\s*[:=]\\s*)(\"?)(?!(?i:bearer|basic)\\b)([^\\s\"&,;}]+)");

  private static final Pattern CARD =
      Pattern.compile("(?<![\\d-])(?:\\d[ -]?){12,18}\\d(?![\\d-])");

  private static final List<Pattern> WHOLE = List.of(PEM, JWT, TOKEN_FORMATS);

  private Redactor() {}

  public static String redact(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    String result = text;
    for (Pattern pattern : WHOLE) {
      result = pattern.matcher(result).replaceAll(MASK);
    }
    result = AUTH_SCHEME.matcher(result).replaceAll("$1$2" + Matcher.quoteReplacement(MASK));
    result = URL_USERINFO.matcher(result).replaceAll("$1" + Matcher.quoteReplacement(MASK) + "@");
    result = SECRET_PAIR.matcher(result).replaceAll("$1$2$3" + Matcher.quoteReplacement(MASK));
    return cards(result);
  }

  /**
   * The secret values {@link #redact} would mask in this text (the value part only, e.g. the token
   * after {@code token=}). Use it to mask the same values when a fragment of the text is quoted
   * elsewhere without the context that identifies it as a secret.
   */
  public static java.util.Set<String> secretValues(String text) {
    java.util.Set<String> values = new java.util.LinkedHashSet<>();
    if (text == null || text.isEmpty()) {
      return values;
    }
    for (Pattern pattern : WHOLE) {
      pattern.matcher(text).results().forEach(r -> values.add(r.group()));
    }
    AUTH_SCHEME
        .matcher(text)
        .results()
        .forEach(r -> values.add(r.group().substring(r.end(2) - r.start())));
    URL_USERINFO
        .matcher(text)
        .results()
        .forEach(
            r -> values.add(r.group().substring(r.end(1) - r.start(), r.group().length() - 1)));
    SECRET_PAIR.matcher(text).results().forEach(r -> values.add(r.group(4)));
    Matcher m = CARD.matcher(text);
    while (m.find()) {
      if (luhn(m.group().replaceAll("[ -]", ""))) {
        values.add(m.group());
      }
    }
    values.removeIf(v -> v.length() < 4);
    return values;
  }

  /** Masks the given secret values wherever they occur, then applies {@link #redact}. */
  public static String redact(String text, java.util.Set<String> knownSecrets) {
    if (text == null) {
      return null;
    }
    String result = text;
    for (String secret : knownSecrets) {
      result = result.replace(secret, MASK);
    }
    return redact(result);
  }

  /** Whether {@link #redact} would change the text. */
  public static boolean containsSecret(String text) {
    return text != null && !text.equals(redact(text));
  }

  private static String cards(String text) {
    Matcher m = CARD.matcher(text);
    StringBuilder out = new StringBuilder();
    while (m.find()) {
      String digits = m.group().replaceAll("[ -]", "");
      m.appendReplacement(out, Matcher.quoteReplacement(luhn(digits) ? MASK : m.group()));
    }
    m.appendTail(out);
    return out.toString();
  }

  static boolean luhn(String digits) {
    if (digits.length() < 13 || digits.length() > 19) {
      return false;
    }
    int sum = 0;
    boolean doubled = false;
    for (int i = digits.length() - 1; i >= 0; i--) {
      int d = digits.charAt(i) - '0';
      if (doubled) {
        d *= 2;
        if (d > 9) {
          d -= 9;
        }
      }
      sum += d;
      doubled = !doubled;
    }
    return sum % 10 == 0;
  }
}

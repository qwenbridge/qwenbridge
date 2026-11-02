package io.qwenbridge.core.security;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Context-aware output sanitization policy (QB-060).
 *
 * <p>QwenBridge does not "sanitize input". The user's query is kept exactly as sent and is only
 * normalized in meaning-preserving ways for understanding; stripping characters from it would
 * change what the user asked for and still not make it safe everywhere. Untrusted text is made safe
 * where it leaves the system, by the rules of the place it goes to:
 *
 * <ul>
 *   <li>{@link Sink#LOG}: secrets masked; line breaks, other control characters and bidirectional
 *       overrides escaped so a value cannot forge log lines or hide text; length bounded.
 *   <li>{@link Sink#ERROR_MESSAGE}: as LOG, plus Java class names removed and a tighter bound, so
 *       an error body never reveals internals.
 *   <li>{@link Sink#HEADER}: printable ASCII only, so no header can be split or injected.
 *   <li>{@link Sink#PROMPT_DATA}: encoded as a JSON string literal, so untrusted text reaches a
 *       model only as quoted data, never as instructions or structure.
 * </ul>
 *
 * <p>Backend queries are a separate rule: values are bound as parameters or typed structures by the
 * compilers, never spliced into query text, so they need no escaping here (QB-059).
 */
public final class Sanitizer {

  public enum Sink {
    LOG(4000),
    ERROR_MESSAGE(300),
    HEADER(256),
    PROMPT_DATA(Integer.MAX_VALUE);

    private final int maxLength;

    Sink(int maxLength) {
      this.maxLength = maxLength;
    }

    public int maxLength() {
      return maxLength;
    }
  }

  static final String TRUNCATED = "…[truncated]";

  private static final Pattern CLASS_NAME =
      Pattern.compile(
          "\\b(?:[a-z_][a-z0-9_]*\\.){2,}[A-Z][A-Za-z0-9_$]*(?:\\.[A-Za-z_][A-Za-z0-9_$]*)*");

  private Sanitizer() {}

  public static String forSink(Sink sink, String text) {
    Objects.requireNonNull(sink, "sink must not be null");
    if (text == null) {
      return "";
    }
    return switch (sink) {
      case LOG -> bound(escapeControls(Redactor.redact(text)), sink.maxLength());
      case ERROR_MESSAGE ->
          bound(
              escapeControls(CLASS_NAME.matcher(Redactor.redact(text)).replaceAll("[internal]")),
              sink.maxLength());
      case HEADER -> bound(printableAscii(text), sink.maxLength());
      case PROMPT_DATA -> jsonString(text);
    };
  }

  /** Escapes C0/C1 controls, line and paragraph separators and bidirectional formatting marks. */
  static String escapeControls(String text) {
    StringBuilder out = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (dangerous(c)) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    return out.toString();
  }

  private static boolean dangerous(char c) {
    return Character.isISOControl(c)
        || c == '\u2028'
        || c == '\u2029'
        || (c >= '\u202a' && c <= '\u202e')
        || (c >= '\u2066' && c <= '\u2069')
        || c == '\u200e'
        || c == '\u200f'
        || c == '\u061c';
  }

  private static String printableAscii(String text) {
    StringBuilder out = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      out.append(c >= 0x20 && c <= 0x7E ? c : '?');
    }
    return out.toString();
  }

  private static String jsonString(String text) {
    StringBuilder out = new StringBuilder(text.length() + 2).append('"');
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20 || c == '\u2028' || c == '\u2029') {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    return out.append('"').toString();
  }

  private static String bound(String text, int max) {
    if (text.length() <= max) {
      return text;
    }
    int cut = max - TRUNCATED.length();
    if (cut > 0 && Character.isHighSurrogate(text.charAt(cut - 1))) {
      cut--;
    }
    return text.substring(0, Math.max(cut, 0)) + TRUNCATED;
  }
}

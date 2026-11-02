package io.qwenbridge.core.text;

import io.qwenbridge.core.request.LanguageTag;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Meaning-preserving normalization of user text for understanding and retrieval (QB-009–QB-012).
 * The original text is never modified; callers keep it alongside the result.
 *
 * <p>Policy, applied in this order:
 *
 * <ol>
 *   <li><b>disallowed-controls</b> (QB-011): removes C0/C1 control characters (tab, CR and LF are
 *       kept and treated as whitespace), explicit bidirectional controls (LRM, RLM, ALM, LRE…PDF,
 *       LRI…PDI), the byte-order mark and soft hyphens. All other format characters are kept,
 *       notably ZWNJ (required for Persian), ZWJ and variation selectors (emoji sequences).
 *   <li><b>unicode-nfc</b> (QB-009): canonical composition only. Compatibility normalization (NFKC)
 *       is deliberately not used here because it changes identifiers and symbols (full-width
 *       product codes, superscripts, ligatures, circled digits).
 *   <li><b>persian-variants</b> (QB-012): only when the language is Persian, Arabic yeh/alef
 *       maksura become Persian yeh and Arabic kaf becomes keheh, and the decorative tatweel is
 *       removed. Arabic text is never rewritten this way.
 *   <li><b>zero-width-non-joiner</b> (QB-012): repeated ZWNJs collapse to one; a ZWNJ next to
 *       whitespace or at either end, where it has no effect, is removed.
 *   <li><b>whitespace</b> (QB-012): every run of Unicode whitespace or space separators becomes one
 *       ASCII space; leading and trailing whitespace is removed.
 * </ol>
 *
 * Not done on purpose: URL or HTML decoding (that is threat canonicalization, a separate
 * representation that is never used for search), case folding, digit or currency conversion,
 * translation. Every step is deterministic and the whole pipeline is idempotent.
 */
public final class SearchTextNormalizer {

  public static final String VERSION = "search-normalization-v1";

  private static final char ZWNJ = '‌';

  private SearchTextNormalizer() {}

  /** One applied step; {@code changed} records whether it altered the text. */
  public record Step(String name, boolean changed) {
    public Step {
      Objects.requireNonNull(name, "name must not be null");
    }
  }

  /** The untouched original, the normalized text and the trace of applied steps. */
  public record Result(String original, String normalized, List<Step> steps) {
    public Result {
      Objects.requireNonNull(original, "original must not be null");
      Objects.requireNonNull(normalized, "normalized must not be null");
      steps = List.copyOf(steps);
    }

    public boolean changed() {
      return !original.equals(normalized);
    }
  }

  public static Result normalize(String original, Optional<LanguageTag> language) {
    String text = original == null ? "" : original;
    boolean persian =
        language != null && language.map(t -> "fa".equals(t.primaryLanguage())).orElse(false);

    List<Step> steps = new ArrayList<>();
    String current = text;
    current =
        apply(
            "disallowed-controls", current, SearchTextNormalizer::removeDisallowedControls, steps);
    current =
        apply("unicode-nfc", current, s -> Normalizer.normalize(s, Normalizer.Form.NFC), steps);
    if (persian) {
      current = apply("persian-variants", current, SearchTextNormalizer::persianVariants, steps);
    }
    current = apply("zero-width-non-joiner", current, SearchTextNormalizer::tidyZwnj, steps);
    current = apply("whitespace", current, SearchTextNormalizer::collapseWhitespace, steps);
    return new Result(text, current, steps);
  }

  private static String apply(
      String name, String input, UnaryOperator<String> operation, List<Step> steps) {
    String output = operation.apply(input);
    steps.add(new Step(name, !output.equals(input)));
    return output;
  }

  static boolean isDisallowedControl(int cp) {
    if (cp == '\t' || cp == '\n' || cp == '\r') {
      return false;
    }
    if (Character.getType(cp) == Character.CONTROL) {
      return true;
    }
    return cp == 0x061C // Arabic letter mark
        || cp == 0x200E // LRM
        || cp == 0x200F // RLM
        || (cp >= 0x202A && cp <= 0x202E) // LRE, RLE, PDF, LRO, RLO
        || (cp >= 0x2066 && cp <= 0x2069) // LRI, RLI, FSI, PDI
        || cp == 0xFEFF // byte-order mark / zero-width no-break space
        || cp == 0x00AD; // soft hyphen
  }

  private static String removeDisallowedControls(String text) {
    StringBuilder out = new StringBuilder(text.length());
    text.codePoints().filter(cp -> !isDisallowedControl(cp)).forEach(out::appendCodePoint);
    return out.toString();
  }

  private static String persianVariants(String text) {
    StringBuilder out = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case 'ي', 'ى' -> out.append('ی'); // Arabic yeh, alef maksura -> Persian yeh
        case 'ك' -> out.append('ک'); // Arabic kaf -> keheh
        case 'ـ' -> {} // tatweel (kashida) is decorative
        default -> out.append(c);
      }
    }
    return out.toString();
  }

  private static String tidyZwnj(String text) {
    if (text.indexOf(ZWNJ) < 0) {
      return text;
    }
    StringBuilder out = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == ZWNJ) {
        boolean duplicate = out.length() > 0 && out.charAt(out.length() - 1) == ZWNJ;
        boolean atStart = out.isEmpty() || isSpace(out.charAt(out.length() - 1));
        int next = i + 1;
        while (next < text.length() && text.charAt(next) == ZWNJ) {
          next++; // look past duplicates, which are removed anyway
        }
        boolean beforeSpaceOrEnd = next >= text.length() || isSpace(text.charAt(next));
        if (duplicate || atStart || beforeSpaceOrEnd) {
          continue;
        }
      }
      out.append(c);
    }
    // A ZWNJ that was kept can now precede a removed character only if it ended up last.
    int end = out.length();
    while (end > 0 && out.charAt(end - 1) == ZWNJ) {
      end--;
    }
    return out.substring(0, end);
  }

  private static boolean isSpace(char c) {
    return Character.isWhitespace(c) || Character.isSpaceChar(c);
  }

  private static String collapseWhitespace(String text) {
    StringBuilder out = new StringBuilder(text.length());
    boolean pendingSpace = false;
    for (int i = 0; i < text.length(); ) {
      int cp = text.codePointAt(i);
      i += Character.charCount(cp);
      if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
        pendingSpace = !out.isEmpty();
        continue;
      }
      if (pendingSpace) {
        out.append(' ');
        pendingSpace = false;
      }
      out.appendCodePoint(cp);
    }
    return out.toString();
  }
}

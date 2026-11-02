package io.qwenbridge.core.retrieval.memory;

import io.qwenbridge.core.text.SearchTextNormalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Deterministic tokenization for in-memory keyword search (QB-108): meaning-preserving search
 * normalization, lower case, then runs of letters and digits in any script. Bounded per text.
 */
public final class Tokenizer {

  public static final int MAX_TOKENS = 4096;
  private static final Pattern SPLIT = Pattern.compile("[^\\p{L}\\p{M}\\p{N}]+");

  private Tokenizer() {}

  public static List<String> tokens(String text) {
    String normalized =
        SearchTextNormalizer.normalize(text == null ? "" : text, Optional.empty())
            .normalized()
            .toLowerCase(Locale.ROOT);
    List<String> out = new ArrayList<>();
    for (String token : SPLIT.split(normalized)) {
      if (!token.isEmpty()) {
        out.add(token);
        if (out.size() == MAX_TOKENS) {
          break;
        }
      }
    }
    return out;
  }
}

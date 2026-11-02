package io.qwenbridge.core.queryir;

import io.qwenbridge.core.request.LanguageTag;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Backend-neutral retrieval requirements (QB-024, QB-040). They are planning inputs, not commands:
 * the Adaptive Planner still checks provider capabilities and policy. No search-engine syntax, SQL
 * or document-store operators ever appear here.
 *
 * @param modes overlapping needs; LEXICAL together with SEMANTIC means hybrid retrieval is useful
 * @param crossLanguage whether matching content in other languages is wanted (QB-020)
 * @param contentLanguages languages the content may be in, when known
 */
public record RetrievalHints(
    Set<Mode> modes, boolean crossLanguage, List<LanguageTag> contentLanguages) {

  public static final RetrievalHints NONE = new RetrievalHints(Set.of(), false, List.of());

  public enum Mode {
    /** Exact identifiers or literal values (SKUs, IPs, pod names). */
    EXACT,
    /** Term-based matching. */
    LEXICAL,
    /** Meaning-based matching (embeddings). */
    SEMANTIC,
    /** Structured filtering on schema fields. */
    STRUCTURED
  }

  public RetrievalHints {
    modes = modes == null || modes.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(modes));
    contentLanguages = contentLanguages == null ? List.of() : List.copyOf(contentLanguages);
  }

  public boolean hybrid() {
    return modes.contains(Mode.LEXICAL) && modes.contains(Mode.SEMANTIC);
  }
}

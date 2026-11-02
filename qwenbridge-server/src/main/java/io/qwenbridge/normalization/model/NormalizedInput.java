package io.qwenbridge.normalization.model;

import java.util.List;

/**
 * Representations of one query. They have different purposes and must not be mixed up:
 *
 * <ul>
 *   <li>{@code originalQuery}: exactly what the user sent; never modified.
 *   <li>{@code normalizedQuery}: meaning-preserving form for understanding and retrieval (NFC,
 *       control and whitespace tidying; no decoding, no compatibility folding).
 *   <li>{@code threatCanonicalQuery}: aggressive canonical form for threat detection only (repeated
 *       URL/HTML decoding, NFKC). It exposes obfuscated payloads and is never used as search or AI
 *       input.
 * </ul>
 *
 * @param trace steps of the threat canonicalization chain
 * @param searchSteps names of the search-normalization steps that changed the text
 */
public record NormalizedInput(
    String originalQuery,
    String normalizedQuery,
    List<NormalizationTraceItem> trace,
    String threatCanonicalQuery,
    List<String> searchSteps) {

  public NormalizedInput {
    originalQuery = safe(originalQuery);
    normalizedQuery = safe(normalizedQuery);
    trace = trace == null ? List.of() : List.copyOf(trace);
    threatCanonicalQuery = threatCanonicalQuery == null ? normalizedQuery : threatCanonicalQuery;
    searchSteps = searchSteps == null ? List.of() : List.copyOf(searchSteps);
  }

  /** Legacy form where one text serves both purposes. */
  public NormalizedInput(
      String originalQuery, String normalizedQuery, List<NormalizationTraceItem> trace) {
    this(originalQuery, normalizedQuery, trace, normalizedQuery, List.of());
  }

  public static NormalizedInput unchanged(String query) {
    String safeQuery = safe(query);
    return new NormalizedInput(safeQuery, safeQuery, List.of());
  }

  public boolean changed() {
    return !originalQuery.equals(normalizedQuery);
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }
}

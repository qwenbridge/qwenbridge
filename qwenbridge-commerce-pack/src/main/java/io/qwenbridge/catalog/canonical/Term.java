package io.qwenbridge.catalog.canonical;

/**
 * A vocabulary-normalized value such as a brand (QB-094).
 *
 * @param value the canonical term when {@code recognized}, otherwise the cleaned source value
 * @param recognized whether a vocabulary entry matched
 */
public record Term(String value, boolean recognized, Provenance provenance) {
  public Term {
    if (value == null || value.isBlank() || provenance == null)
      throw new IllegalArgumentException("Invalid term");
  }
}

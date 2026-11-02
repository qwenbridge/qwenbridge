package io.qwenbridge.catalog.canonical;

/**
 * Where a canonical value came from (QB-092).
 *
 * @param origin how the value was obtained
 * @param sourceField the source path read, for example {@code variants[0].price}
 * @param sourceValue the source value as delivered (after Unicode NFKC and whitespace cleanup),
 *     always kept even when normalization changed it
 * @param rule the vocabulary entry or conversion applied; {@code null} for {@link Origin#SOURCE}
 */
public record Provenance(Origin origin, String sourceField, String sourceValue, String rule) {
  public enum Origin {
    /** Taken from the source unchanged. */
    SOURCE,
    /** Mapped to a canonical term by a vocabulary alias. */
    NORMALIZED,
    /** Computed deterministically from a source value, such as a unit conversion. */
    DERIVED,
    /** Read from free text (the title); uncertain by construction. */
    INFERRED
  }

  public Provenance {
    if (origin == null || sourceField == null || sourceField.isBlank() || sourceValue == null)
      throw new IllegalArgumentException("Invalid provenance");
    if (origin != Origin.SOURCE && (rule == null || rule.isBlank()))
      throw new IllegalArgumentException("Non-source provenance must name its rule");
  }

  public static Provenance source(String field, String value) {
    return new Provenance(Origin.SOURCE, field, value, null);
  }
}

package io.qwenbridge.catalog.canonical;

import java.math.BigDecimal;
import java.util.List;

/**
 * A semantic attribute with provenance and uncertainty (QB-096).
 *
 * <p>Values read from the source are {@link Certainty#CERTAIN}. Values read from free text are
 * {@link Certainty#UNCERTAIN}. When free text names several values, the attribute is {@link
 * Certainty#AMBIGUOUS}: it has no value, only the alternatives. No numeric confidence is invented.
 *
 * @param value canonical string form: text, a plain decimal, or {@code true}/{@code false}; {@code
 *     null} only when ambiguous
 */
public record AttributeValue(
    String name,
    Kind kind,
    String value,
    Certainty certainty,
    List<String> alternatives,
    Provenance provenance) {
  public enum Kind {
    TEXT,
    NUMBER,
    BOOLEAN
  }

  public enum Certainty {
    CERTAIN,
    UNCERTAIN,
    AMBIGUOUS
  }

  public AttributeValue {
    if (name == null || name.isBlank() || kind == null || certainty == null || provenance == null)
      throw new IllegalArgumentException("Invalid attribute");
    alternatives = List.copyOf(alternatives);
    if (certainty == Certainty.AMBIGUOUS) {
      if (value != null || alternatives.size() < 2)
        throw new IllegalArgumentException("Ambiguous attribute needs alternatives and no value");
    } else {
      if (value == null || !alternatives.isEmpty())
        throw new IllegalArgumentException("Attribute value required");
      if (kind == Kind.NUMBER) new BigDecimal(value);
      if (kind == Kind.BOOLEAN && !value.equals("true") && !value.equals("false"))
        throw new IllegalArgumentException("Boolean attribute must be true or false");
    }
    if ((certainty == Certainty.CERTAIN) == (provenance.origin() == Provenance.Origin.INFERRED))
      throw new IllegalArgumentException("Only inferred attributes are uncertain");
  }

  public static AttributeValue certain(String name, Kind kind, String value, Provenance source) {
    return new AttributeValue(name, kind, value, Certainty.CERTAIN, List.of(), source);
  }

  /** Whether the value was asserted by the source (possibly normalized or converted). */
  public boolean asserted() {
    return certainty == Certainty.CERTAIN;
  }
}

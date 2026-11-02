package io.qwenbridge.analysis.model;

/**
 * Grounded constraints: a field requirement extracted from text, with the evidence span it was read
 * from (QB-148). These types belong to query analysis, not to any domain; Commerce, the v1 pipeline
 * and the model-output parser all use them. Evidence offsets always refer to the named source text.
 */
public final class Grounded {
  private Grounded() {}

  public enum Operator {
    EQ,
    NE,
    LT,
    LTE,
    GT,
    GTE
  }

  public record Evidence(String source, int start, int end, String quote) {
    public Evidence {
      if (source == null || quote == null || start < 0 || end <= start)
        throw new IllegalArgumentException("Invalid evidence span");
    }

    public void verify(String text) {
      if (text == null || end > text.length() || !text.substring(start, end).equals(quote))
        throw new IllegalArgumentException("Evidence does not match source");
    }
  }

  public record Constraint(String field, Operator operator, String value, Evidence evidence) {
    public Constraint {
      if (field == null || operator == null || value == null || value.isBlank() || evidence == null)
        throw new IllegalArgumentException("Incomplete constraint");
      if (value.length() > 256) throw new IllegalArgumentException("Constraint value too long");
    }
  }
}

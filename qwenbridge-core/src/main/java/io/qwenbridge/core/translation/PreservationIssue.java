package io.qwenbridge.core.translation;

import java.util.List;
import java.util.Objects;

/**
 * One kind of meaning that a translation lost or added (QB-018).
 *
 * @param subjects normalized values involved, for internal diagnostics (for example {@code 1500},
 *     {@code EUR}, {@code LT}, {@code brand:Nike}). They come from the user's own text; API views
 *     expose only the kind, direction and count.
 */
public record PreservationIssue(Kind kind, Direction direction, List<String> subjects) {

  public enum Kind {
    /** Numeric values, in any digit script. */
    NUMBER,
    /** Currencies next to a number. */
    CURRENCY,
    /** Unit symbols next to a number (kg, cm, GB, %). */
    UNIT,
    /** Comparison and range operators on values. */
    OPERATOR,
    /** Exclusions. */
    NEGATION,
    /** Dictionary entities and other entities with a canonical value. */
    ENTITY,
    /** Exact identifiers (codes, IP addresses, UUIDs), which must survive verbatim. */
    IDENTIFIER
  }

  public enum Direction {
    /** In the source but not in the translation. */
    LOST,
    /** In the translation but not in the source: the translator invented it. */
    ADDED
  }

  public PreservationIssue {
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(direction, "direction must not be null");
    subjects = List.copyOf(subjects);
    if (subjects.isEmpty()) {
      throw new IllegalArgumentException("an issue needs at least one subject");
    }
  }

  public int count() {
    return subjects.size();
  }
}

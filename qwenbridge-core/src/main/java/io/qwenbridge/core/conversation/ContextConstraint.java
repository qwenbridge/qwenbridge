package io.qwenbridge.core.conversation;

import java.util.Objects;

/**
 * A constraint carried from one conversation turn to the next, with its provenance (QB-144).
 *
 * @param operator the domain's operator name (EQ, NE, LT, …)
 * @param turn the turn that introduced it (1 = first)
 */
public record ContextConstraint(
    String field, String operator, String value, Origin origin, int turn) {

  public enum Origin {
    /** Stated by the user in a turn of this conversation. */
    USER_TEXT,
    /** Supplied by the client without the server's signature: re-validated, never trusted. */
    CONTEXT,
    /** From a relaxation proposal the user accepted. */
    ACCEPTED_PROPOSAL,
    /** From a suggestion the user applied. */
    SUGGESTION,
    /** From the user's negative feedback. */
    FEEDBACK
  }

  public ContextConstraint {
    Objects.requireNonNull(origin, "origin must not be null");
    if (field == null || !field.matches("[A-Za-z_][A-Za-z0-9_.]{0,63}")) {
      throw new IllegalArgumentException("invalid context field");
    }
    if (operator == null || !operator.matches("[A-Z_]{2,16}")) {
      throw new IllegalArgumentException("invalid context operator");
    }
    if (value == null || value.isBlank() || value.length() > 256) {
      throw new IllegalArgumentException("context values have 1..256 characters");
    }
    if (turn < 1) {
      throw new IllegalArgumentException("turns start at 1");
    }
  }

  ContextConstraint withOrigin(Origin newOrigin) {
    return new ContextConstraint(field, operator, value, newOrigin, turn);
  }
}

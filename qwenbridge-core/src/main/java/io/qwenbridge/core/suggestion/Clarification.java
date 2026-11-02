package io.qwenbridge.core.suggestion;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A targeted clarification question (QB-135): what is unclear, about which field, and the answers
 * that would resolve it. Generic "please rephrase" questions are used only when nothing more
 * specific is known ({@link Kind#VAGUE}).
 */
public record Clarification(
    Kind kind, Optional<String> field, String question, List<String> options) {
  public enum Kind {
    /** Two requirements on the field cannot both hold; choose one. */
    CONFLICT,
    /** A value needs a qualifier the query did not give (e.g. a price without a currency). */
    MISSING_QUALIFIER,
    /** The field or operator is not supported by the schema. */
    UNSUPPORTED,
    /** A value is not valid for the field. */
    INVALID_VALUE,
    /** The request as a whole is unclear. */
    VAGUE
  }

  public Clarification {
    Objects.requireNonNull(kind, "kind must not be null");
    field = field == null ? Optional.empty() : field;
    Objects.requireNonNull(question, "question must not be null");
    options = List.copyOf(options);
  }
}

package io.qwenbridge.commerce;

import io.qwenbridge.commerce.CommerceContracts.ClarificationQuestion;
import io.qwenbridge.core.validation.ConstraintAssessment;
import java.util.List;
import java.util.Optional;

/**
 * Targeted clarification questions (QB-135). Each question names the field and, where the answer is
 * one of a known set (conflicting values or limits), offers exactly those answers. A generic
 * question is used only when nothing more specific is known.
 */
final class CommerceClarifications {

  private CommerceClarifications() {}

  static ClarificationQuestion vague(String question) {
    return new ClarificationQuestion("VAGUE", null, question, List.of());
  }

  static ClarificationQuestion of(IllegalArgumentException ex) {
    if (!(ex instanceof CommerceCompiler.Rejected r)) return vague(ex.getMessage());
    String field = r.field().orElse(null);
    String label = field == null ? "this request" : field;
    String question =
        switch (r.kind()) {
          case CONFLICT ->
              r.getMessage().startsWith("Empty range")
                  ? "The "
                      + label
                      + " limits "
                      + String.join(" and ", r.options())
                      + " cannot both hold. Which limit should apply?"
                  : "You asked for more than one "
                      + label
                      + ": "
                      + String.join(" or ", r.options())
                      + ". Which one do you want?";
          case MISSING_QUALIFIER ->
              "currency".equals(field)
                  ? "Which currency is the price in?"
                  : "Which unit does the " + label + " use?";
          case UNSUPPORTED ->
              "Searching by " + label + " is not supported here. Should it be left out?";
          case INVALID_VALUE -> "The " + label + " value is not valid. What value did you mean?";
          case VAGUE -> "Please narrow the request: " + r.getMessage();
        };
    return new ClarificationQuestion(r.kind().name(), field, question, r.options());
  }

  static ClarificationQuestion of(ConstraintAssessment assessment, Optional<String> field) {
    String label = field.orElse("a condition");
    return switch (assessment.status()) {
      case CONTRADICTORY ->
          new ClarificationQuestion(
              "CONFLICT",
              field.orElse(null),
              "The requirements on " + label + " cannot all hold. Which one should apply?",
              List.of());
      default ->
          new ClarificationQuestion(
              "UNSUPPORTED",
              field.orElse(null),
              "Searching by " + label + " is not supported here. Should it be left out?",
              List.of());
    };
  }
}

package io.qwenbridge.core.intent;

import java.util.List;
import java.util.Objects;

/**
 * Whether a request can be executed as understood (QB-026, QB-027). Uncertainty is explicit:
 * nothing is fabricated to fill a gap.
 *
 * @param status overall outcome
 * @param issues the concrete reasons, empty when {@code CLEAR}
 * @param questions focused clarification questions, one per blocking issue
 */
public record QueryAssessment(Status status, List<Issue> issues, List<String> questions) {

  public enum Status {
    /** Executable as understood. */
    CLEAR,
    /** Meaningful information is missing; ask before executing. */
    NEEDS_CLARIFICATION,
    /** The request targets a domain that is not enabled in this deployment. */
    OUT_OF_DOMAIN
  }

  public enum Issue {
    /** No subject: only vague words such as "the best option". */
    NO_SUBJECT,
    /** The client named a domain that is not enabled. */
    DOMAIN_NOT_ENABLED
  }

  public QueryAssessment {
    Objects.requireNonNull(status, "status must not be null");
    issues = issues == null ? List.of() : List.copyOf(issues);
    questions = questions == null ? List.of() : List.copyOf(questions);
    if ((status == Status.CLEAR) != issues.isEmpty()) {
      throw new IllegalArgumentException("CLEAR exactly when there are no issues");
    }
  }

  public static QueryAssessment clear() {
    return new QueryAssessment(Status.CLEAR, List.of(), List.of());
  }

  public boolean executable() {
    return status == Status.CLEAR;
  }
}

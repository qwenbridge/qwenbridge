package io.qwenbridge.core.explain;

import io.qwenbridge.core.confidence.Confidence;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An explanation of one decision (QB-081, QB-082). Only {@link Status#SUPPORTED} explanations carry
 * a statement, and a supported statement must cite evidence. When there is nothing recorded to
 * build a statement from, the status says so ({@link Status#UNKNOWN}, {@link
 * Status#INSUFFICIENT_EVIDENCE}) and no statement is made up.
 *
 * @param subject what is explained, e.g. {@code constraint:c1}, {@code intent:i1}, {@code plan}
 * @param statement built from recorded fields by a template; never free model text
 * @param evidenceIds evidence the statement rests on
 */
public record Explanation(
    String subject,
    Status status,
    Optional<String> statement,
    List<String> evidenceIds,
    Confidence confidence) {

  public enum Status {
    SUPPORTED,
    /** Nothing was recorded about how this was decided. */
    UNKNOWN,
    /** A decision was recorded, but there is no evidence that grounds it. */
    INSUFFICIENT_EVIDENCE
  }

  public Explanation {
    Objects.requireNonNull(subject, "subject must not be null");
    Objects.requireNonNull(status, "status must not be null");
    statement = statement == null ? Optional.empty() : statement;
    evidenceIds = List.copyOf(evidenceIds);
    Objects.requireNonNull(confidence, "confidence must not be null");
    if (status == Status.SUPPORTED && (statement.isEmpty() || evidenceIds.isEmpty())) {
      throw new IllegalArgumentException("a supported explanation needs a statement and evidence");
    }
    if (status != Status.SUPPORTED && statement.isPresent()) {
      throw new IllegalArgumentException("only supported explanations make a statement");
    }
  }

  public static Explanation supported(
      String subject, String statement, List<String> evidenceIds, Confidence confidence) {
    return new Explanation(
        subject, Status.SUPPORTED, Optional.of(statement), evidenceIds, confidence);
  }

  public static Explanation unknown(String subject) {
    return new Explanation(
        subject, Status.UNKNOWN, Optional.empty(), List.of(), Confidence.unknown());
  }

  public static Explanation insufficient(String subject, Confidence confidence) {
    return new Explanation(
        subject, Status.INSUFFICIENT_EVIDENCE, Optional.empty(), List.of(), confidence);
  }
}

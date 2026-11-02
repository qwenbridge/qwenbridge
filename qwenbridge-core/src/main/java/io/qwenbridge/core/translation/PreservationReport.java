package io.qwenbridge.core.translation;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Whether a translation kept the meaning that filters depend on (QB-018).
 *
 * @param issues what was lost or added
 * @param unverified kinds that could not be fully checked for this language pair (for example
 *     operators in a language whose cues are not in the lexicon). Unverified is never reported as
 *     preserved.
 */
public record PreservationReport(
    List<PreservationIssue> issues, Set<PreservationIssue.Kind> unverified) {

  public enum Status {
    /** Every kind was checked and nothing was lost or added. */
    PRESERVED,
    /** Nothing checked was lost or added, but some kinds could not be checked. */
    PARTIALLY_VERIFIED,
    /** Something was lost or added. */
    NOT_PRESERVED
  }

  public PreservationReport {
    issues = List.copyOf(issues);
    unverified =
        unverified == null || unverified.isEmpty()
            ? Set.of()
            : Set.copyOf(EnumSet.copyOf(unverified));
  }

  public Status status() {
    if (!issues.isEmpty()) {
      return Status.NOT_PRESERVED;
    }
    return unverified.isEmpty() ? Status.PRESERVED : Status.PARTIALLY_VERIFIED;
  }

  public boolean preserved() {
    return issues.isEmpty();
  }

  public List<PreservationIssue> lost() {
    return issues.stream().filter(i -> i.direction() == PreservationIssue.Direction.LOST).toList();
  }
}

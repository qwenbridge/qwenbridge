package io.qwenbridge.core.explain;

import io.qwenbridge.core.evidence.EvidenceRecord;
import io.qwenbridge.core.evidence.EvidenceSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Explanations together with the evidence they cite (QB-080–QB-082). Construct with {@link
 * #checked}: any supported explanation whose evidence is missing, or whose user-text span no longer
 * matches the original query, is downgraded to INSUFFICIENT_EVIDENCE and loses its statement.
 */
public record ExplanationSet(List<Explanation> explanations, EvidenceSet evidence) {

  public ExplanationSet {
    explanations = List.copyOf(explanations);
    evidence = evidence == null ? EvidenceSet.EMPTY : evidence;
  }

  public static ExplanationSet checked(
      List<Explanation> explanations, EvidenceSet evidence, String originalText) {
    List<Explanation> kept = new ArrayList<>();
    for (Explanation e : explanations) {
      kept.add(
          grounded(e, evidence, originalText)
              ? e
              : Explanation.insufficient(e.subject(), e.confidence()));
    }
    return new ExplanationSet(kept, evidence);
  }

  private static boolean grounded(Explanation e, EvidenceSet evidence, String originalText) {
    if (e.status() != Explanation.Status.SUPPORTED) {
      return true;
    }
    for (String id : e.evidenceIds()) {
      var record = evidence.find(id);
      if (record.isEmpty()) {
        return false;
      }
      EvidenceRecord r = record.get();
      if (r.kind() == EvidenceRecord.Kind.USER_TEXT
          && !r.span().orElseThrow().matches(originalText)) {
        return false;
      }
    }
    return true;
  }
}

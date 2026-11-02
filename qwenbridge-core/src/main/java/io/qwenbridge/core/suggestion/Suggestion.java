package io.qwenbridge.core.suggestion;

import java.util.List;
import java.util.Objects;

/**
 * A typed suggestion (QB-132).
 *
 * @param changes the exact constraint changes applying it makes; empty only for a rephrasing
 * @param grounded true when the suggestion is backed by evidence in the current results: {@code
 *     support} documents satisfy it. An ungrounded suggestion (a model rephrasing) is labelled and
 *     ranked after every grounded one.
 * @param support how many current results satisfy the suggestion; 0 when ungrounded
 * @param evidence ids of up to five supporting results
 */
public record Suggestion(
    String id,
    Kind kind,
    Source source,
    String text,
    List<Change> changes,
    boolean grounded,
    int support,
    List<String> evidence,
    int rank) {
  public enum Kind {
    /** Narrows the current results. */
    REFINE,
    /** Says the same thing differently. */
    REPHRASE
  }

  public enum Source {
    /** Facet values observed in the current results. */
    RESULT_FACETS,
    /** A language model's rephrasing; not grounded. */
    MODEL_REWRITE
  }

  public Suggestion {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(text, "text must not be null");
    changes = List.copyOf(changes);
    evidence = List.copyOf(evidence);
    if (grounded != support > 0 || support < 0) {
      throw new IllegalArgumentException(
          "a grounded suggestion has support, an ungrounded one none");
    }
    if (kind == Kind.REFINE && changes.isEmpty()) {
      throw new IllegalArgumentException("a refinement changes something");
    }
  }

  Suggestion at(int newRank) {
    return new Suggestion(id, kind, source, text, changes, grounded, support, evidence, newRank);
  }
}

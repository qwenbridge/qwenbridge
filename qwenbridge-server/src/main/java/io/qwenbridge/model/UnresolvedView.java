package io.qwenbridge.model;

import io.qwenbridge.core.extraction.Unresolved;

/** Something in the query that was not resolved because doing so would require guessing. */
public record UnresolvedView(String kind, int start, int end, String detail) {

  public static UnresolvedView from(Unresolved unresolved) {
    return new UnresolvedView(
        unresolved.kind().name(),
        unresolved.span().start(),
        unresolved.span().end(),
        unresolved.detail());
  }
}

package io.qwenbridge.core.extraction;

import io.qwenbridge.core.queryir.Span;
import java.util.Objects;

/**
 * Something the user said that could not be resolved without guessing (QB-030, QB-031). It is
 * reported instead of being silently dropped or filled in.
 */
public record Unresolved(Kind kind, Span span, String detail) {

  public enum Kind {
    /** A currency word or symbol that names several currencies (krona, dollar, ¥). */
    AMBIGUOUS_CURRENCY,
    /** A number whose separators allow two readings (e.g. "1.200"). */
    AMBIGUOUS_NUMBER,
    /** A relative time window without a reference time. */
    MISSING_REFERENCE_TIME,
    /** A calendar expression ("yesterday") without a time zone. */
    MISSING_TIME_ZONE,
    /** A value whose field could not be bound to the active schema. */
    UNBOUND_FIELD
  }

  public Unresolved {
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(span, "span must not be null");
    Objects.requireNonNull(detail, "detail must not be null");
  }
}

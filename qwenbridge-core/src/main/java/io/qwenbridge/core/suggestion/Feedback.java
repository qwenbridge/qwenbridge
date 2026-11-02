package io.qwenbridge.core.suggestion;

import io.qwenbridge.core.queryir.Constraint.Operator;
import io.qwenbridge.core.retrieval.filter.FieldValue;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Negative feedback as a typed context update (QB-136). Feedback never edits a query silently: it
 * becomes an explicit update the caller shows and applies.
 *
 * <ul>
 *   <li>NOT_RELEVANT on a result: that result is suppressed in the follow-up; nothing else changes.
 *   <li>EXCLUDE_VALUE on a field: an exclusion (NE) is added, as if the user had typed "not …".
 *   <li>TOO_EXPENSIVE on a result: an upper bound below that result's value of {@code field} is
 *       added as a SOFT preference: it ranks, never filters, because "too expensive" does not say
 *       by how much.
 * </ul>
 */
public final class Feedback {

  public enum Kind {
    NOT_RELEVANT,
    EXCLUDE_VALUE,
    TOO_EXPENSIVE
  }

  /**
   * What the user said. {@code documentId} for result feedback, {@code field}/{@code value}
   * otherwise.
   */
  public record Signal(
      Kind kind, Optional<String> documentId, Optional<String> field, Optional<FieldValue> value) {
    public Signal {
      Objects.requireNonNull(kind, "kind must not be null");
      documentId = documentId == null ? Optional.empty() : documentId;
      field = field == null ? Optional.empty() : field;
      value = value == null ? Optional.empty() : value;
      switch (kind) {
        case NOT_RELEVANT -> require(documentId.isPresent(), "documentId");
        case EXCLUDE_VALUE -> require(field.isPresent() && value.isPresent(), "field and value");
        case TOO_EXPENSIVE ->
            require(documentId.isPresent() && field.isPresent(), "documentId and field");
      }
    }

    private static void require(boolean ok, String what) {
      if (!ok) {
        throw new IllegalArgumentException("feedback requires " + what);
      }
    }
  }

  /**
   * The typed update.
   *
   * @param hard changes to the query's HARD constraints
   * @param preferences SOFT preferences added for ranking
   * @param suppressed result ids left out of the follow-up
   */
  public record Update(List<Change> hard, List<Change> preferences, List<String> suppressed) {
    public Update {
      hard = List.copyOf(hard);
      preferences = List.copyOf(preferences);
      suppressed = List.copyOf(suppressed);
    }
  }

  private Feedback() {}

  /**
   * @param results the current results' fields by id; result feedback must name one of them
   */
  public static Update interpret(Signal signal, Map<String, Map<String, FieldValue>> results) {
    return switch (signal.kind()) {
      case NOT_RELEVANT -> {
        known(signal, results);
        yield new Update(List.of(), List.of(), List.of(signal.documentId().get()));
      }
      case EXCLUDE_VALUE ->
          new Update(
              List.of(Change.add(signal.field().get(), Operator.NE, signal.value().get())),
              List.of(),
              List.of());
      case TOO_EXPENSIVE -> {
        var fields = known(signal, results);
        FieldValue v = fields.get(signal.field().get());
        if (!(v instanceof FieldValue.Number n)) {
          throw new IllegalArgumentException("the result has no numeric " + signal.field().get());
        }
        yield new Update(
            List.of(),
            List.of(
                Change.add(signal.field().get(), Operator.LT, new FieldValue.Number(n.value()))),
            List.of(signal.documentId().get()));
      }
    };
  }

  private static Map<String, FieldValue> known(
      Signal signal, Map<String, Map<String, FieldValue>> results) {
    var fields = results.get(signal.documentId().get());
    if (fields == null) {
      throw new IllegalArgumentException("feedback must name a result of this search");
    }
    return fields;
  }
}

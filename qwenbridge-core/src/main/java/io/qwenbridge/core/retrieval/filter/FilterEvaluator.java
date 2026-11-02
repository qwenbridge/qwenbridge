package io.qwenbridge.core.retrieval.filter;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Reference evaluation of {@link Filter} semantics, used by the in-memory providers. */
public final class FilterEvaluator {

  private static final Pattern TOKEN = Pattern.compile("[^\\p{L}\\p{N}]+");

  private FilterEvaluator() {}

  public static boolean matches(Filter filter, Map<String, FieldValue> fields) {
    return switch (filter) {
      case Filter.All a -> true;
      case Filter.And a -> a.filters().stream().allMatch(f -> matches(f, fields));
      case Filter.Or o -> o.filters().stream().anyMatch(f -> matches(f, fields));
      case Filter.Not n -> !matches(n.filter(), fields);
      case Filter.Compare c -> compare(c, fields.get(c.field()));
      case Filter.In in -> {
        FieldValue v = fields.get(in.field());
        yield v != null && in.values().stream().anyMatch(x -> equal(v, x));
      }
      case Filter.Contains c -> contains(fields.get(c.field()), c.term());
      case Filter.Exists e -> fields.containsKey(e.field());
    };
  }

  private static boolean compare(Filter.Compare c, FieldValue actual) {
    if (actual == null) {
      return c.op() == Filter.Compare.Op.NE;
    }
    return switch (c.op()) {
      case EQ -> equal(actual, c.value());
      case NE -> !equal(actual, c.value());
      case LT, LTE, GT, GTE -> {
        if (!(actual instanceof FieldValue.Number a)
            || !(c.value() instanceof FieldValue.Number b)) {
          yield false;
        }
        int cmp = a.value().compareTo(b.value());
        yield switch (c.op()) {
          case LT -> cmp < 0;
          case LTE -> cmp <= 0;
          case GT -> cmp > 0;
          default -> cmp >= 0;
        };
      }
    };
  }

  static boolean equal(FieldValue a, FieldValue b) {
    if (a instanceof FieldValue.Number x && b instanceof FieldValue.Number y) {
      return x.value().compareTo(y.value()) == 0;
    }
    if (a instanceof FieldValue.TextList list && b instanceof FieldValue.Text t) {
      return list.values().contains(t.value());
    }
    return a.equals(b);
  }

  private static boolean contains(FieldValue value, String term) {
    String needle = term.toLowerCase(Locale.ROOT);
    if (value instanceof FieldValue.Text t) {
      for (String token : TOKEN.split(t.value().toLowerCase(Locale.ROOT))) {
        if (token.equals(needle)) {
          return true;
        }
      }
      return false;
    }
    if (value instanceof FieldValue.TextList list) {
      return list.values().stream().anyMatch(v -> v.toLowerCase(Locale.ROOT).equals(needle));
    }
    return false;
  }
}

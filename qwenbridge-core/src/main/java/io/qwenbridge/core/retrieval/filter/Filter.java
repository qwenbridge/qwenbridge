package io.qwenbridge.core.retrieval.filter;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Backend-neutral filter expression (QB-100). Connectors translate it into their own query
 * language; user values only ever appear as typed leaf values, never as query text.
 *
 * <p>Semantics, identical in every provider:
 *
 * <ul>
 *   <li>Comparisons are exact: text by equality, numbers by decimal value. A value of another type
 *       never matches.
 *   <li>A missing field makes a positive comparison false. {@link Compare.Op#NE}, {@link Not} and
 *       therefore exclusions are true for a missing field: a document without a material is not
 *       leather.
 *   <li>{@link Contains} matches a whole token of a text field or an element of a text list,
 *       case-insensitively.
 * </ul>
 */
public sealed interface Filter {

  /** Matches every document of the tenant. */
  record All() implements Filter {}

  record And(List<Filter> filters) implements Filter {
    public And {
      filters = List.copyOf(filters);
    }
  }

  record Or(List<Filter> filters) implements Filter {
    public Or {
      filters = List.copyOf(filters);
      if (filters.isEmpty()) {
        throw new IllegalArgumentException("an OR needs at least one branch");
      }
    }
  }

  record Not(Filter filter) implements Filter {
    public Not {
      Objects.requireNonNull(filter, "filter must not be null");
    }
  }

  record Compare(String field, Op op, FieldValue value) implements Filter {
    public enum Op {
      EQ,
      NE,
      LT,
      LTE,
      GT,
      GTE
    }

    public Compare {
      Objects.requireNonNull(field, "field must not be null");
      Objects.requireNonNull(op, "op must not be null");
      Objects.requireNonNull(value, "value must not be null");
    }
  }

  record In(String field, List<FieldValue> values) implements Filter {
    public In {
      Objects.requireNonNull(field, "field must not be null");
      values = List.copyOf(values);
      if (values.isEmpty()) {
        throw new IllegalArgumentException("IN needs at least one value");
      }
    }
  }

  record Contains(String field, String term) implements Filter {
    public Contains {
      Objects.requireNonNull(field, "field must not be null");
      Objects.requireNonNull(term, "term must not be null");
    }
  }

  record Exists(String field) implements Filter {
    public Exists {
      Objects.requireNonNull(field, "field must not be null");
    }
  }

  Filter ALL = new All();

  /** Features a provider must support to evaluate this filter (QB-103). */
  default Set<FilterFeature> features() {
    Set<FilterFeature> out = EnumSet.noneOf(FilterFeature.class);
    collect(this, out);
    return out;
  }

  private static void collect(Filter f, Set<FilterFeature> out) {
    switch (f) {
      case All a -> {}
      case And a -> a.filters().forEach(x -> collect(x, out));
      case Or o -> {
        out.add(FilterFeature.OR);
        o.filters().forEach(x -> collect(x, out));
      }
      case Not n -> {
        out.add(FilterFeature.NOT);
        collect(n.filter(), out);
      }
      case Compare c ->
          out.add(
              switch (c.op()) {
                case EQ -> FilterFeature.EQUALITY;
                case NE -> FilterFeature.NOT;
                default -> FilterFeature.RANGE;
              });
      case In i -> out.add(FilterFeature.IN);
      case Contains c -> out.add(FilterFeature.CONTAINS);
      case Exists e -> out.add(FilterFeature.EXISTS);
    }
  }
}

package io.qwenbridge.connectors;

import io.qwenbridge.core.retrieval.filter.FieldValue;
import io.qwenbridge.core.retrieval.filter.Filter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Translates the neutral {@link Filter} into the OpenSearch bool/filter DSL (QB-162), following
 * {@link io.qwenbridge.core.retrieval.filter.FilterEvaluator}: a missing field fails positive
 * comparisons and satisfies NE and NOT; ordering applies to numbers only; values are typed leaves,
 * never query text. The tenant term is always added here, never taken from the filter.
 *
 * <p>QB-287: moved here from the removed Elasticsearch/OpenSearch connector module, because the
 * built-in optional OpenSearch backend uses it.
 */
final class OpenSearchFilters {
  static final Map<String, Object> NONE =
      Map.of("bool", Map.of("must_not", List.of(Map.of("match_all", Map.of()))));

  private OpenSearchFilters() {}

  /** The tenant-scoped filter; {@code exact} names the exact-match form of a field. */
  static Map<String, Object> translate(String tenant, Filter filter, UnaryOperator<String> exact) {
    return Map.of(
        "bool",
        Map.of("filter", List.of(Map.of("term", Map.of("tenantId", tenant)), es(filter, exact))));
  }

  static Map<String, Object> es(Filter filter, UnaryOperator<String> exact) {
    return switch (filter) {
      case Filter.All a -> Map.of("match_all", Map.of());
      case Filter.And a ->
          Map.of("bool", Map.of("filter", a.filters().stream().map(f -> es(f, exact)).toList()));
      case Filter.Or o ->
          Map.of(
              "bool",
              Map.of(
                  "should",
                  o.filters().stream().map(f -> es(f, exact)).toList(),
                  "minimum_should_match",
                  1));
      case Filter.Not n -> Map.of("bool", Map.of("must_not", List.of(es(n.filter(), exact))));
      case Filter.Compare c -> compare(c, exact);
      case Filter.In in ->
          Map.of(
              "bool",
              Map.of(
                  "should",
                  in.values().stream()
                      .map(
                          v ->
                              compare(
                                  new Filter.Compare(in.field(), Filter.Compare.Op.EQ, v), exact))
                      .toList(),
                  "minimum_should_match",
                  1));
      case Filter.Contains c ->
          Map.of("match", Map.of(c.field(), Map.of("query", c.term(), "operator", "and")));
      case Filter.Exists e -> Map.of("exists", Map.of("field", e.field()));
    };
  }

  private static Map<String, Object> compare(Filter.Compare c, UnaryOperator<String> exact) {
    if (c.op() == Filter.Compare.Op.NE) {
      return Map.of(
          "bool",
          Map.of(
              "must_not",
              List.of(
                  compare(new Filter.Compare(c.field(), Filter.Compare.Op.EQ, c.value()), exact))));
    }
    return switch (c.value()) {
      case FieldValue.Text t ->
          c.op() == Filter.Compare.Op.EQ
              ? Map.of("term", Map.of(exact.apply(c.field()), t.value()))
              : NONE;
      case FieldValue.Number n ->
          c.op() == Filter.Compare.Op.EQ
              ? Map.of("term", Map.of(c.field(), n.value()))
              : Map.of(
                  "range",
                  Map.of(c.field(), Map.of(c.op().name().toLowerCase(Locale.ROOT), n.value())));
      case FieldValue.Bool b ->
          c.op() == Filter.Compare.Op.EQ ? Map.of("term", Map.of(c.field(), b.value())) : NONE;
      case FieldValue.TextList l -> NONE;
    };
  }
}

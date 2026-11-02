package io.qwenbridge.connectors;

import io.qwenbridge.core.retrieval.filter.FieldValue;
import io.qwenbridge.core.retrieval.filter.Filter;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Translates the backend-neutral {@link Filter} AST into the built-in OpenSearch backend's query
 * language and stored documents back into typed fields (QB-100, connector side). Every translation
 * follows {@link io.qwenbridge.core.retrieval.filter.FilterEvaluator} exactly, and a live
 * conformance test checks that each backend returns the same documents as the reference evaluator:
 *
 * <ul>
 *   <li>a missing field fails positive comparisons and satisfies NE and NOT;
 *   <li>a value of another type never matches (ordering applies to numbers only);
 *   <li>values are always bound parameters or typed leaves, never query text (QB-059).
 * </ul>
 *
 * The tenant is always added by the translator, never taken from the filter.
 */
public final class NeutralFilterTranslator {

  private NeutralFilterTranslator() {}

  // ---------------------------------------------------------------- OpenSearch

  /** Fields mapped as keyword (exact) without a {@code .keyword} subfield. */
  static final Set<String> ES_KEYWORD_FIELDS =
      Set.of("category", "currency", "tenantId", "documentId", "domain");

  /** Analyzed text fields with no exact form. */
  static final Set<String> ES_TEXT_ONLY_FIELDS = Set.of("title", "description");

  static String esExactField(String field) {
    return ES_KEYWORD_FIELDS.contains(field) || ES_TEXT_ONLY_FIELDS.contains(field)
        ? field
        : field + ".keyword";
  }

  static final Map<String, Object> ES_NONE =
      Map.of("bool", Map.of("must_not", List.of(Map.of("match_all", Map.of()))));

  /** The OpenSearch filter, with the Commerce index convention for exact fields. */
  public static Map<String, Object> openSearch(String tenant, Filter filter) {
    return OpenSearchFilters.translate(tenant, filter, NeutralFilterTranslator::esExactField);
  }

  // ---------------------------------------------------------------- results

  /** Converts a stored document into typed fields for re-validation (QB-104, QB-122). */
  public static Map<String, FieldValue> fields(Map<String, Object> document) {
    Map<String, FieldValue> out = new HashMap<>();
    document.forEach(
        (k, v) -> {
          FieldValue f = value(v);
          if (f != null) {
            out.put(k, f);
          }
        });
    return out;
  }

  static FieldValue value(Object v) {
    if (v instanceof String s) {
      return new FieldValue.Text(s);
    }
    if (v instanceof Boolean b) {
      return new FieldValue.Bool(b);
    }
    if (v instanceof BigDecimal d) {
      return new FieldValue.Number(d);
    }
    if (v instanceof Number n) {
      return new FieldValue.Number(new BigDecimal(n.toString()));
    }
    if (v instanceof List<?> list && list.stream().allMatch(x -> x instanceof String)) {
      return new FieldValue.TextList(list.stream().map(Object::toString).toList());
    }
    return null;
  }
}

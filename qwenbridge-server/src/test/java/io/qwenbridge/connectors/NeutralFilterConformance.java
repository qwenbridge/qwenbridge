package io.qwenbridge.connectors;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.core.retrieval.CancellationToken;
import io.qwenbridge.core.retrieval.ScoredDocument;
import io.qwenbridge.core.retrieval.SearchOutcome;
import io.qwenbridge.core.retrieval.SearchProvider;
import io.qwenbridge.core.retrieval.SearchRequest;
import io.qwenbridge.core.retrieval.filter.FieldValue;
import io.qwenbridge.core.retrieval.filter.Filter;
import io.qwenbridge.core.retrieval.filter.FilterEvaluator;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Shared fixtures of the neutral-filter conformance checks (QB-100, QB-175): documents with missing
 * fields and mixed types, filters covering every operator, the reference result computed by {@link
 * FilterEvaluator}, and a provider's actual result. QB-287: kept from the removed
 * Elasticsearch/PostgreSQL/MongoDB conformance test for the retained OpenSearch conformance test.
 */
final class NeutralFilterConformance {

  static final String TENANT = "neutral-a";
  static final String OTHER = "neutral-b";

  private NeutralFilterConformance() {}

  static final Map<String, Map<String, Object>> DOCS = new LinkedHashMap<>();

  static {
    DOCS.put("n1", doc("Nike running shoes", "Nike", "black", "89.90", true, "mesh"));
    DOCS.put("n2", doc("Nike leather sneakers", "Nike", "white", "129", true, "leather"));
    DOCS.put("n3", doc("Adidas trail shoes", "Adidas", "black", "99", false, null));
    DOCS.put("n4", doc("Ecco boots", "Ecco", "brown", "149.5", true, "leather"));
    DOCS.put("n5", doc("Puma socks", "Puma", null, "9", true, "cotton"));
  }

  static Map<String, Object> doc(
      String title, String brand, String color, String price, boolean inStock, String material) {
    Map<String, Object> d = new HashMap<>();
    d.put("title", title);
    d.put("brand", brand);
    if (color != null) d.put("color", color);
    d.put("price", new BigDecimal(price));
    d.put("inStock", inStock);
    if (material != null) d.put("condition", material);
    d.put("category", "shoes");
    return d;
  }

  static final List<Filter> FILTERS =
      List.of(
          Filter.ALL,
          new Filter.Compare("brand", Filter.Compare.Op.EQ, FieldValue.text("Nike")),
          new Filter.Compare("color", Filter.Compare.Op.NE, FieldValue.text("black")),
          new Filter.Compare("condition", Filter.Compare.Op.NE, FieldValue.text("leather")),
          new Filter.Compare("price", Filter.Compare.Op.LT, FieldValue.number("100")),
          new Filter.Compare("price", Filter.Compare.Op.GTE, FieldValue.number("129")),
          new Filter.Compare("brand", Filter.Compare.Op.GT, FieldValue.text("A")),
          new Filter.Compare("inStock", Filter.Compare.Op.EQ, new FieldValue.Bool(false)),
          new Filter.In("brand", List.of(FieldValue.text("Ecco"), FieldValue.text("Puma"))),
          new Filter.Not(new Filter.Exists("color")),
          new Filter.Or(
              List.of(
                  new Filter.Compare("brand", Filter.Compare.Op.EQ, FieldValue.text("Adidas")),
                  new Filter.Compare("price", Filter.Compare.Op.GT, FieldValue.number("140")))),
          new Filter.And(
              List.of(
                  new Filter.Compare("brand", Filter.Compare.Op.EQ, FieldValue.text("Nike")),
                  new Filter.Not(
                      new Filter.Compare(
                          "condition", Filter.Compare.Op.EQ, FieldValue.text("leather"))),
                  new Filter.Compare("price", Filter.Compare.Op.LTE, FieldValue.number("89.9")))));

  static TreeSet<String> expected(Filter f) {
    TreeSet<String> ids = new TreeSet<>();
    DOCS.forEach(
        (id, d) -> {
          if (FilterEvaluator.matches(f, NeutralFilterTranslator.fields(d))) ids.add(id);
        });
    return ids;
  }

  static TreeSet<String> actual(SearchProvider provider, Filter f) {
    SearchOutcome outcome =
        provider.search(SearchRequest.keyword(TENANT, "", f, 50), CancellationToken.create());
    assertThat(outcome).as(provider.id() + " " + f).isInstanceOf(SearchOutcome.Success.class);
    TreeSet<String> ids = new TreeSet<>();
    for (ScoredDocument d : ((SearchOutcome.Success) outcome).hits()) ids.add(d.id());
    return ids;
  }
}

package io.qwenbridge.connectors;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.core.retrieval.filter.FieldValue;
import io.qwenbridge.core.retrieval.filter.Filter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * QB-287: the OpenSearch filter translation, moved from the removed Elasticsearch/OpenSearch
 * connector module into the built-in optional OpenSearch backend, keeps the reference semantics of
 * {@link io.qwenbridge.core.retrieval.filter.FilterEvaluator}: tenant term always added, exact
 * fields for text equality, numbers as terms or ranges, NE as must_not, impossible comparisons as
 * match-none, never query text built from values.
 */
class OpenSearchFiltersTest {

  static Map<String, Object> translate(Filter f) {
    return NeutralFilterTranslator.openSearch("tenant-a", f);
  }

  @Test
  void theTenantTermIsAlwaysAdded() {
    assertThat(translate(Filter.ALL).toString())
        .contains("{term={tenantId=tenant-a}}")
        .contains("match_all");
  }

  @Test
  void textEqualityUsesTheExactFieldAndNumbersUseRanges() {
    var eq =
        translate(new Filter.Compare("brand", Filter.Compare.Op.EQ, new FieldValue.Text("Nike")));
    assertThat(eq.toString()).contains("{term={brand.keyword=Nike}}");
    var keyword =
        translate(new Filter.Compare("currency", Filter.Compare.Op.EQ, new FieldValue.Text("EUR")));
    assertThat(keyword.toString()).contains("{term={currency=EUR}}");
    var lt =
        translate(
            new Filter.Compare(
                "price", Filter.Compare.Op.LT, new FieldValue.Number(new BigDecimal("100"))));
    assertThat(lt.toString()).contains("{range={price={lt=100}}}");
  }

  @Test
  void exclusionsAndImpossibleComparisonsAreExplicit() {
    var ne =
        translate(
            new Filter.Compare(
                "condition", Filter.Compare.Op.NE, new FieldValue.Text("refurbished")));
    assertThat(ne.toString()).contains("must_not").contains("condition.keyword=refurbished");
    // Ordering applies to numbers only: a text range matches nothing instead of comparing strings.
    var textRange =
        translate(new Filter.Compare("brand", Filter.Compare.Op.LT, new FieldValue.Text("N")));
    assertThat(textRange.toString()).contains("must_not=[{match_all={}}]");
  }

  @Test
  void combinationsKeepTheirStructure() {
    var any =
        translate(
            new Filter.In(
                "color", List.of(new FieldValue.Text("red"), new FieldValue.Text("blue"))));
    assertThat(any.toString()).contains("minimum_should_match=1").contains("color.keyword=red");
    var not = translate(new Filter.Not(new Filter.Exists("size")));
    assertThat(not.toString()).contains("must_not=[{exists={field=size}}]");
  }
}

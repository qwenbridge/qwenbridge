package io.qwenbridge.execution.provider.opensearch.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.qwenbridge.execution.provider.model.SearchRequest;
import io.qwenbridge.execution.provider.opensearch.OpenSearchProperties;
import io.qwenbridge.intelligence.ir.QueryOperator;
import io.qwenbridge.intelligence.planning.LogicalQueryPlan;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenSearchStructuredQueryFactoryTest {
  private final OpenSearchQueryFactory factory =
      new OpenSearchQueryFactory(
          new OpenSearchProperties(
              "http://localhost:9200",
              "products",
              10,
              Duration.ofSeconds(5),
              Duration.ofSeconds(30)));

  private LogicalQueryPlan plan(String currency, QueryOperator operator) {
    return new LogicalQueryPlan(
        1,
        "mouse",
        List.of(),
        List.of(
            new LogicalQueryPlan.PriceUpperBound(
                "price", operator, new BigDecimal("100.00"), currency)));
  }

  @Test
  void compilesStrictPriceAndCurrencyFilters() {
    var query =
        factory
            .from(
                SearchRequest.keyword("mouse"),
                plan("SEK", QueryOperator.LESS_THAN),
                "price",
                "currency.keyword")
            .query();
    Map<?, ?> bool = (Map<?, ?>) query.get("bool");
    assertThat(bool.get("must").toString()).contains("multi_match");
    assertThat(bool.get("filter").toString()).contains("lt=100.00", "currency.keyword=SEK");
  }

  @Test
  void compilesInclusivePriceFilter() {
    var query =
        factory.from(
            SearchRequest.keyword("mouse"),
            plan("EUR", QueryOperator.LESS_THAN_OR_EQUAL),
            "price",
            "currency.keyword");
    assertThat(query.query().toString()).contains("lte=100.00");
  }

  @Test
  void rejectsVectorSearchRatherThanDroppingFilters() {
    assertThatThrownBy(
            () ->
                factory.from(
                    SearchRequest.vector("mouse", List.of(0.1)),
                    plan("SEK", QueryOperator.LESS_THAN),
                    "price",
                    "currency.keyword"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsMismatchedQuery() {
    assertThatThrownBy(
            () ->
                factory.from(
                    SearchRequest.keyword("other"),
                    plan("SEK", QueryOperator.LESS_THAN),
                    "price",
                    "currency.keyword"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsUntrustedFieldName() {
    assertThatThrownBy(
            () ->
                factory.from(
                    SearchRequest.keyword("mouse"),
                    plan("SEK", QueryOperator.LESS_THAN),
                    "price;drop",
                    "currency.keyword"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void preservesLegacyQueryWhenNoFilters() {
    var plan = new LogicalQueryPlan(1, "mouse", List.of(), List.of());
    assertThat(factory.from(SearchRequest.keyword("mouse"), plan, "price", "currency.keyword"))
        .isEqualTo(factory.from(SearchRequest.keyword("mouse")));
  }

  @Test
  void rejectsMultipleCurrenciesWithoutConversion() {
    var plan =
        new LogicalQueryPlan(
            1,
            "mouse",
            List.of(),
            List.of(
                new LogicalQueryPlan.PriceUpperBound(
                    "price", QueryOperator.LESS_THAN, BigDecimal.TEN, "SEK"),
                new LogicalQueryPlan.PriceUpperBound(
                    "price", QueryOperator.LESS_THAN, BigDecimal.ONE, "EUR")));
    assertThatThrownBy(
            () -> factory.from(SearchRequest.keyword("mouse"), plan, "price", "currency.keyword"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}

package io.qwenbridge.connectors;

import static org.assertj.core.api.Assertions.*;

import io.qwenbridge.analysis.model.Grounded.*;
import io.qwenbridge.commerce.CommerceCompiler;
import io.qwenbridge.commerce.CommerceContracts.*;
import io.qwenbridge.commerce.CommerceSchemaRegistry;
import io.qwenbridge.core.retrieval.CancellationToken;
import io.qwenbridge.core.retrieval.ScoredDocument;
import io.qwenbridge.core.retrieval.SearchOutcome;
import io.qwenbridge.core.retrieval.SearchRequest;
import io.qwenbridge.core.retrieval.filter.FieldValue;
import io.qwenbridge.core.retrieval.filter.Filter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** QB-091 / QB-107: the in-process backend on both contracts. */
class MemoryCommerceBackendTest {
  private static MemoryCommerceBackend seeded() {
    var backend = new MemoryCommerceBackend();
    backend.upsert(
        "t1",
        "a",
        Map.of(
            "title",
            "Nike running shoes",
            "brand",
            "Nike",
            "price",
            new BigDecimal("80"),
            "currency",
            "EUR",
            "condition",
            "new"));
    backend.upsert(
        "t1",
        "b",
        Map.of(
            "title",
            "Adidas running shoes",
            "brand",
            "Adidas",
            "price",
            new BigDecimal("120"),
            "currency",
            "EUR"));
    backend.upsert(
        "t2",
        "c",
        Map.of(
            "title",
            "Nike running shoes",
            "brand",
            "Nike",
            "price",
            new BigDecimal("1"),
            "currency",
            "EUR"));
    return backend;
  }

  private static Constraint c(String field, Operator op, String value) {
    return new Constraint(field, op, value, new Evidence("original", 0, 1, "x"));
  }

  private static Plan plan(String tenant, Constraint... constraints) {
    return CommerceCompiler.validate(
        CommerceSchemaRegistry.defaults(), tenant, List.of(constraints));
  }

  @Test
  void commerceQueriesRankByTextAndApplyThePlanWithTheResultCheckSemantics() {
    var backend = seeded();
    var all =
        backend.search(
            new CommerceBackend.Query("running", plan("t1"), "KEYWORD", List.of(), 10, 0));
    assertThat(all.totalHits()).isEqualTo(2);
    var nike =
        backend.search(
            new CommerceBackend.Query(
                "running shoes",
                plan("t1", c("brand", Operator.EQ, "nike")),
                "KEYWORD",
                List.of(),
                10,
                0));
    assertThat(nike.hits()).extracting(h -> h.id()).containsExactly("a");
    // Commerce NE requires the field: "b" has no condition and is excluded, as the result check
    // would reject it.
    var notUsed =
        backend.search(
            new CommerceBackend.Query(
                "", plan("t1", c("condition", Operator.NE, "used")), "KEYWORD", List.of(), 10, 0));
    assertThat(notUsed.hits()).extracting(h -> h.id()).containsExactly("a");
    for (var hit : notUsed.hits())
      assertThat(
              CommerceCompiler.matches(
                  plan("t1", c("condition", Operator.NE, "used")), hit.document()))
          .isTrue();
    var page =
        backend.search(new CommerceBackend.Query("", plan("t1"), "KEYWORD", List.of(), 1, 1));
    assertThat(page.hits()).hasSize(1);
    assertThat(page.totalHits()).isEqualTo(2);
    assertThatThrownBy(
            () ->
                backend.search(
                    new CommerceBackend.Query("x", plan("t1"), "HYBRID", List.of(0.1), 10, 0)))
        .hasMessageContaining("does not support");
  }

  @Test
  void unifiedSpiUsesTheNeutralSemanticsWithinTheTenant() {
    var backend = seeded();
    SearchOutcome outcome =
        backend.search(
            SearchRequest.keyword(
                "t1",
                "",
                new Filter.Compare("condition", Filter.Compare.Op.NE, FieldValue.text("used")),
                10),
            CancellationToken.create());
    // Neutral NE: a missing field satisfies it.
    assertThat(((SearchOutcome.Success) outcome).hits())
        .extracting(ScoredDocument::id)
        .containsExactly("a", "b");
    backend.delete("t1", "a");
    var after =
        backend.search(
            SearchRequest.keyword("t1", "nike", Filter.ALL, 10), CancellationToken.create());
    assertThat(((SearchOutcome.Success) after).hits()).isEmpty();
    assertThat(backend.size()).isEqualTo(2);
  }
}

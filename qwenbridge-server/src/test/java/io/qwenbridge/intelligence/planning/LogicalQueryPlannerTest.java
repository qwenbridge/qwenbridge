package io.qwenbridge.intelligence.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.qwenbridge.intelligence.constraint.QueryConstraintValidator;
import io.qwenbridge.intelligence.ir.QueryConstraint;
import io.qwenbridge.intelligence.ir.QueryEntity;
import io.qwenbridge.intelligence.ir.QueryIR;
import io.qwenbridge.intelligence.ir.QueryOperator;
import io.qwenbridge.intelligence.schema.QuerySchemaRegistry;
import io.qwenbridge.intelligence.schema.QuerySchemaValidator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class LogicalQueryPlannerTest {
  private final LogicalQueryPlanner planner =
      new LogicalQueryPlanner(
          new QueryConstraintValidator(new QuerySchemaValidator(new QuerySchemaRegistry())));

  private static QueryConstraint price(QueryOperator op, String amount, String currency) {
    return new QueryConstraint("price", op, new BigDecimal(amount), currency, 0, 5);
  }

  private static QueryIR ir(List<QueryEntity> entities, QueryConstraint... constraints) {
    return new QueryIR(
        QueryIR.CURRENT_VERSION, "Apple under 100 SEK", entities, List.of(constraints));
  }

  @Test
  void createsPlanWithoutConstraints() {
    var plan = planner.plan(ir(List.of()));
    assertThat(plan.irVersion()).isEqualTo(QueryIR.CURRENT_VERSION);
    assertThat(plan.priceUpperBounds()).isEmpty();
  }

  @Test
  void preservesSourceGroundedEntities() {
    var entity = new QueryEntity("Apple", "BRAND", 0, 5);
    assertThat(planner.plan(ir(List.of(entity))).entities()).containsExactly(entity);
  }

  @Test
  void choosesTightestUpperBound() {
    var plan =
        planner.plan(
            ir(
                List.of(),
                price(QueryOperator.LESS_THAN, "100", "SEK"),
                price(QueryOperator.LESS_THAN_OR_EQUAL, "80", "SEK")));
    assertThat(plan.priceUpperBounds()).hasSize(1);
    assertThat(plan.priceUpperBounds().getFirst().amount()).isEqualByComparingTo("80");
  }

  @Test
  void strictBoundWinsWhenAmountsEqual() {
    var plan =
        planner.plan(
            ir(
                List.of(),
                price(QueryOperator.LESS_THAN_OR_EQUAL, "80", "SEK"),
                price(QueryOperator.LESS_THAN, "80.00", "SEK")));
    assertThat(plan.priceUpperBounds().getFirst().operator()).isEqualTo(QueryOperator.LESS_THAN);
  }

  @Test
  void neverCombinesDifferentCurrencies() {
    var plan =
        planner.plan(
            ir(
                List.of(),
                price(QueryOperator.LESS_THAN, "100", "SEK"),
                price(QueryOperator.LESS_THAN, "80", "EUR")));
    assertThat(plan.priceUpperBounds())
        .extracting(LogicalQueryPlan.PriceUpperBound::currency)
        .containsExactly("SEK", "EUR");
  }

  @Test
  void rejectsUnknownCurrencyBeforePlanning() {
    assertThatThrownBy(
            () -> planner.plan(ir(List.of(), price(QueryOperator.LESS_THAN, "80", "GBP"))))
        .isInstanceOf(QueryPlanningException.class)
        .hasMessageContaining("currency");
  }

  @Test
  void rejectsDuplicateConstraints() {
    assertThatThrownBy(
            () ->
                planner.plan(
                    ir(
                        List.of(),
                        price(QueryOperator.LESS_THAN, "80", "SEK"),
                        price(QueryOperator.LESS_THAN, "80.00", "SEK"))))
        .isInstanceOf(QueryPlanningException.class)
        .hasMessageContaining("duplicate");
  }

  @Test
  void planCollectionsAreImmutable() {
    var plan = planner.plan(ir(List.of(), price(QueryOperator.LESS_THAN, "80", "SEK")));
    assertThatThrownBy(() -> plan.priceUpperBounds().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }
}

package io.qwenbridge.intelligence.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.qwenbridge.execution.provider.implementation.OpenSearchProvider;
import io.qwenbridge.execution.provider.model.SearchRequest;
import io.qwenbridge.intelligence.constraint.QueryConstraintValidator;
import io.qwenbridge.intelligence.extraction.QueryEntityConstraintExtractor;
import io.qwenbridge.intelligence.ir.QueryIRMapper;
import io.qwenbridge.intelligence.planning.LogicalQueryPlan;
import io.qwenbridge.intelligence.planning.LogicalQueryPlanner;
import io.qwenbridge.intelligence.schema.QuerySchemaRegistry;
import io.qwenbridge.intelligence.schema.QuerySchemaValidator;
import io.qwenbridge.semantic.SemanticAnalysis;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StructuredQueryCoordinatorTest {
  private final OpenSearchProvider provider = mock(OpenSearchProvider.class);
  private final StructuredQueryCoordinator coordinator =
      new StructuredQueryCoordinator(
          new QueryEntityConstraintExtractor(),
          new QueryIRMapper(),
          new LogicalQueryPlanner(
              new QueryConstraintValidator(new QuerySchemaValidator(new QuerySchemaRegistry()))),
          provider);

  @Test
  void preparesGroundedPriceConstraint() {
    LogicalQueryPlan plan =
        coordinator.prepare(SemanticAnalysis.basic("Gaming mouse under 100 SEK"));
    assertEquals(1, plan.priceUpperBounds().size());
    assertEquals("SEK", plan.priceUpperBounds().getFirst().currency());
  }

  @Test
  void passesTrustedFieldsAndLogicalPlanToProvider() {
    coordinator.execute(
        SemanticAnalysis.basic("Gaming mouse under 100 SEK"), "price", "currency.keyword");
    ArgumentCaptor<LogicalQueryPlan> plans = ArgumentCaptor.forClass(LogicalQueryPlan.class);
    verify(provider)
        .searchStructured(
            eq(SearchRequest.keyword("Gaming mouse under 100 SEK")),
            plans.capture(),
            eq("price"),
            eq("currency.keyword"));
    assertEquals(1, plans.getValue().priceUpperBounds().size());
  }

  @Test
  void refusesUnfilteredStructuredExecution() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            coordinator.execute(
                SemanticAnalysis.basic("Gaming mouse"), "price", "currency.keyword"));
    verifyNoInteractions(provider);
  }

  @Test
  void refusesMixedCurrencyPriceBoundsBeforeCallingProvider() {
    var mixed =
        new LogicalQueryPlan(
            1,
            "mouse under 100 SEK and 80 EUR",
            java.util.List.of(),
            java.util.List.of(
                new LogicalQueryPlan.PriceUpperBound(
                    "price",
                    io.qwenbridge.intelligence.ir.QueryOperator.LESS_THAN,
                    new java.math.BigDecimal("100"),
                    "SEK"),
                new LogicalQueryPlan.PriceUpperBound(
                    "price",
                    io.qwenbridge.intelligence.ir.QueryOperator.LESS_THAN,
                    new java.math.BigDecimal("80"),
                    "EUR")));
    var mockedPlanner = mock(LogicalQueryPlanner.class);
    when(mockedPlanner.plan(any())).thenReturn(mixed);
    var guarded =
        new StructuredQueryCoordinator(
            new QueryEntityConstraintExtractor(), new QueryIRMapper(), mockedPlanner, provider);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            guarded.execute(
                SemanticAnalysis.basic("mouse under 100 SEK and 80 EUR"),
                "price",
                "currency.keyword"));
    verifyNoInteractions(provider);
  }
}

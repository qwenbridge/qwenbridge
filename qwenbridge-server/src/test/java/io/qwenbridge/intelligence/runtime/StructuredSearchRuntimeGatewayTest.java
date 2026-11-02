package io.qwenbridge.intelligence.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.qwenbridge.execution.provider.model.SearchResponse;
import io.qwenbridge.semantic.SemanticAnalysis;
import org.junit.jupiter.api.Test;

class StructuredSearchRuntimeGatewayTest {
  private final StructuredQueryCoordinator coordinator = mock(StructuredQueryCoordinator.class);
  private final SemanticAnalysis analysis = SemanticAnalysis.basic("mouse under 100 SEK");

  @Test
  void disabledByDefaultBoundaryDoesNotExecute() {
    var gateway =
        new StructuredSearchRuntimeGateway(coordinator, false, "price", "currency.keyword");
    assertThrows(
        IllegalStateException.class, () -> gateway.executeAfterPolicyApproval(analysis, true));
    verifyNoInteractions(coordinator);
  }

  @Test
  void policyDenialDoesNotExecute() {
    var gateway =
        new StructuredSearchRuntimeGateway(coordinator, true, "price", "currency.keyword");
    assertThrows(
        SecurityException.class, () -> gateway.executeAfterPolicyApproval(analysis, false));
    verifyNoInteractions(coordinator);
  }

  @Test
  void approvedExecutionUsesOperatorControlledMapping() {
    var gateway =
        new StructuredSearchRuntimeGateway(coordinator, true, "product.price", "currency.keyword");
    when(coordinator.execute(analysis, "product.price", "currency.keyword"))
        .thenReturn(SearchResponse.empty());
    assertEquals(SearchResponse.empty(), gateway.executeAfterPolicyApproval(analysis, true));
    verify(coordinator).execute(analysis, "product.price", "currency.keyword");
  }

  @Test
  void rejectsInvalidMappingNames() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new StructuredSearchRuntimeGateway(
                coordinator, true, "price);drop", "currency.keyword"));
    verifyNoInteractions(coordinator);
  }

  @Test
  void rejectsIdenticalPriceAndCurrencyMappings() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new StructuredSearchRuntimeGateway(coordinator, true, "price", "price"));
    verifyNoInteractions(coordinator);
  }
}

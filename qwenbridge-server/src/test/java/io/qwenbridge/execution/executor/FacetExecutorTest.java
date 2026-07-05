package io.qwenbridge.execution.executor;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.execution.ExecutionOperation;
import io.qwenbridge.execution.ExecutionStep;
import org.junit.jupiter.api.Test;

class FacetExecutorTest {

  @Test
  void shouldExecuteFacetOperation() {
    FacetExecutor executor = new FacetExecutor();

    assertThat(executor.operation()).isEqualTo(ExecutionOperation.APPLY_FACETS);

    assertThat(executor.execute(new ExecutionStep(10, ExecutionOperation.APPLY_FACETS, "facets")))
        .containsExactly("facet-placeholder-result");
  }
}

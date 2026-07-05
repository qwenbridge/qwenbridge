package io.qwenbridge.execution.executor;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.execution.ExecutionOperation;
import io.qwenbridge.execution.ExecutionStep;
import org.junit.jupiter.api.Test;

class HybridSearchExecutorTest {

  @Test
  void shouldExecuteHybridSearchOperation() {
    HybridSearchExecutor executor = new HybridSearchExecutor();

    assertThat(executor.operation()).isEqualTo(ExecutionOperation.HYBRID_SEARCH);

    assertThat(executor.execute(new ExecutionStep(10, ExecutionOperation.HYBRID_SEARCH, "hybrid")))
        .containsExactly("hybrid-search-placeholder-result");
  }
}

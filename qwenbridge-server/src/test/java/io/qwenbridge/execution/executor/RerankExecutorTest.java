package io.qwenbridge.execution.executor;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.execution.ExecutionOperation;
import io.qwenbridge.execution.ExecutionStep;
import org.junit.jupiter.api.Test;

class RerankExecutorTest {

  @Test
  void shouldExecuteRerankOperation() {
    RerankExecutor executor = new RerankExecutor();

    assertThat(executor.operation()).isEqualTo(ExecutionOperation.RERANK_RESULTS);

    assertThat(executor.execute(new ExecutionStep(10, ExecutionOperation.RERANK_RESULTS, "rerank")))
        .containsExactly("rerank-placeholder-result");
  }
}

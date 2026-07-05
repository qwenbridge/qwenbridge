package io.qwenbridge.execution.executor;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.execution.ExecutionOperation;
import io.qwenbridge.execution.ExecutionStep;
import org.junit.jupiter.api.Test;

class ReturnResultsExecutorTest {

  @Test
  void shouldExecuteReturnResultsOperation() {
    ReturnResultsExecutor executor = new ReturnResultsExecutor();

    assertThat(executor.operation()).isEqualTo(ExecutionOperation.RETURN_RESULTS);

    assertThat(executor.execute(new ExecutionStep(10, ExecutionOperation.RETURN_RESULTS, "return")))
        .containsExactly("return-results-placeholder");
  }
}

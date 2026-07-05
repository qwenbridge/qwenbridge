package io.qwenbridge.execution.executor;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.execution.ExecutionOperation;
import io.qwenbridge.execution.ExecutionStep;
import org.junit.jupiter.api.Test;

class DirectAnswerExecutorTest {

  @Test
  void shouldExecuteDirectAnswerOperation() {
    DirectAnswerExecutor executor = new DirectAnswerExecutor();

    assertThat(executor.operation()).isEqualTo(ExecutionOperation.DIRECT_ANSWER);

    assertThat(executor.execute(new ExecutionStep(10, ExecutionOperation.DIRECT_ANSWER, "answer")))
        .containsExactly("direct-answer-placeholder-result");
  }
}

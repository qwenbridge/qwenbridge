package io.qwenbridge.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutionResultTest {

  @Test
  void shouldCreateCompletedExecutionResult() {
    ExecutionResult result =
        ExecutionResult.completed(
            List.of(ExecutionOperation.KEYWORD_SEARCH, ExecutionOperation.RETURN_RESULTS),
            List.of("table", "desk"),
            "Execution completed.");

    assertThat(result.executed()).isTrue();
    assertThat(result.operations())
        .containsExactly(ExecutionOperation.KEYWORD_SEARCH, ExecutionOperation.RETURN_RESULTS);
    assertThat(result.results()).containsExactly("table", "desk");
  }

  @Test
  void shouldCreateSkippedExecutionResult() {
    ExecutionResult result = ExecutionResult.skipped("No execution plan available.");

    assertThat(result.executed()).isFalse();
    assertThat(result.operations()).isEmpty();
    assertThat(result.results()).isEmpty();
    assertThat(result.reason()).isEqualTo("No execution plan available.");
  }

  @Test
  void shouldUseDefaultReasonWhenBlank() {
    ExecutionResult result =
        ExecutionResult.completed(List.of(ExecutionOperation.RETURN_RESULTS), List.of(), " ");

    assertThat(result.reason()).isEqualTo("No execution result reason provided.");
  }
}

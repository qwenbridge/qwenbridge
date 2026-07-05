package io.qwenbridge.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutionPlanTest {

  @Test
  void shouldCreateExecutionPlanWithSortedSteps() {
    ExecutionPlan plan =
        ExecutionPlan.builder()
            .mode(SearchMode.HYBRID)
            .backend(SearchBackend.IN_MEMORY)
            .steps(
                List.of(
                    new ExecutionStep(30, ExecutionOperation.RERANK_RESULTS, "rerank"),
                    new ExecutionStep(10, ExecutionOperation.KEYWORD_SEARCH, "keyword"),
                    new ExecutionStep(20, ExecutionOperation.VECTOR_SEARCH, "vector")))
            .reason("Hybrid execution plan")
            .build();

    assertThat(plan.steps())
        .extracting(ExecutionStep::operation)
        .containsExactly(
            ExecutionOperation.KEYWORD_SEARCH,
            ExecutionOperation.VECTOR_SEARCH,
            ExecutionOperation.RERANK_RESULTS);
  }

  @Test
  void shouldDetectOperationInPlan() {
    ExecutionPlan plan =
        ExecutionPlan.builder()
            .mode(SearchMode.KEYWORD)
            .backend(SearchBackend.IN_MEMORY)
            .steps(
                List.of(
                    new ExecutionStep(10, ExecutionOperation.KEYWORD_SEARCH, "keyword"),
                    new ExecutionStep(20, ExecutionOperation.RETURN_RESULTS, "return")))
            .reason("Keyword execution plan")
            .build();

    assertThat(plan.contains(ExecutionOperation.KEYWORD_SEARCH)).isTrue();
    assertThat(plan.contains(ExecutionOperation.VECTOR_SEARCH)).isFalse();
  }

  @Test
  void shouldRejectEmptySteps() {
    assertThatThrownBy(
            () ->
                ExecutionPlan.builder()
                    .mode(SearchMode.KEYWORD)
                    .backend(SearchBackend.IN_MEMORY)
                    .steps(List.of())
                    .reason("Invalid plan")
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("steps must not be empty");
  }
}

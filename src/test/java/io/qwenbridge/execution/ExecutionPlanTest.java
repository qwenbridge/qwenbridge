package io.qwenbridge.execution;

import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionPlanTest {

    @Test
    void shouldCreateExecutionPlanWithSortedSteps() {
        ExecutionPlan plan = new ExecutionPlan(
                SearchMode.HYBRID,
                SearchBackend.IN_MEMORY,
                List.of(
                        new ExecutionStep(30, ExecutionOperation.RERANK_RESULTS, "rerank"),
                        new ExecutionStep(10, ExecutionOperation.KEYWORD_SEARCH, "keyword"),
                        new ExecutionStep(20, ExecutionOperation.VECTOR_SEARCH, "vector")
                ),
                "Hybrid execution plan"
        );

        assertThat(plan.steps())
                .extracting(ExecutionStep::operation)
                .containsExactly(
                        ExecutionOperation.KEYWORD_SEARCH,
                        ExecutionOperation.VECTOR_SEARCH,
                        ExecutionOperation.RERANK_RESULTS
                );
    }

    @Test
    void shouldDetectOperationInPlan() {
        ExecutionPlan plan = new ExecutionPlan(
                SearchMode.KEYWORD,
                SearchBackend.IN_MEMORY,
                List.of(
                        new ExecutionStep(10, ExecutionOperation.KEYWORD_SEARCH, "keyword"),
                        new ExecutionStep(20, ExecutionOperation.RETURN_RESULTS, "return")
                ),
                "Keyword execution plan"
        );

        assertThat(plan.contains(ExecutionOperation.KEYWORD_SEARCH)).isTrue();
        assertThat(plan.contains(ExecutionOperation.VECTOR_SEARCH)).isFalse();
    }

    @Test
    void shouldRejectEmptySteps() {
        assertThatThrownBy(() -> new ExecutionPlan(
                SearchMode.KEYWORD,
                SearchBackend.IN_MEMORY,
                List.of(),
                "Invalid plan"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("steps must not be empty");
    }
}

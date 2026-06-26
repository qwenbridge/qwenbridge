package io.qwenbridge.execution;

import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchDecision;
import io.qwenbridge.decision.SearchMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionPlanFactoryTest {

    private final ExecutionPlanFactory factory = new ExecutionPlanFactory();

    @Test
    void shouldCreateKeywordExecutionPlan() {
        ExecutionPlan plan = factory.from(SearchDecision.keyword());

        assertThat(plan.mode()).isEqualTo(SearchMode.KEYWORD);
        assertThat(plan.backend()).isEqualTo(SearchBackend.IN_MEMORY);
        assertThat(plan.contains(ExecutionOperation.KEYWORD_SEARCH)).isTrue();
        assertThat(plan.contains(ExecutionOperation.RETURN_RESULTS)).isTrue();
        assertThat(plan.contains(ExecutionOperation.VECTOR_SEARCH)).isFalse();
    }

    @Test
    void shouldCreateHybridExecutionPlan() {
        ExecutionPlan plan = factory.from(SearchDecision.hybrid());

        assertThat(plan.contains(ExecutionOperation.HYBRID_SEARCH)).isTrue();
        assertThat(plan.contains(ExecutionOperation.APPLY_FACETS)).isTrue();
        assertThat(plan.contains(ExecutionOperation.RERANK_RESULTS)).isTrue();
        assertThat(plan.contains(ExecutionOperation.RETURN_RESULTS)).isTrue();
    }

    @Test
    void shouldCreateDirectAnswerExecutionPlanWithoutReturnResults() {
        ExecutionPlan plan = factory.from(SearchDecision.directAnswer());

        assertThat(plan.mode()).isEqualTo(SearchMode.DIRECT_ANSWER);
        assertThat(plan.backend()).isEqualTo(SearchBackend.NONE);
        assertThat(plan.contains(ExecutionOperation.DIRECT_ANSWER)).isTrue();
        assertThat(plan.contains(ExecutionOperation.RETURN_RESULTS)).isFalse();
    }

    @Test
    void shouldAddRewriteStepBeforeSearchWhenRequested() {
        SearchDecision decision = new SearchDecision(
                SearchMode.KEYWORD,
                SearchBackend.IN_MEMORY,
                true,
                false,
                false,
                false,
                false,
                true,
                false,
                0.75,
                "Rewrite before keyword search."
        );

        ExecutionPlan plan = factory.from(decision);

        assertThat(plan.steps())
                .extracting(ExecutionStep::operation)
                .containsExactly(
                        ExecutionOperation.REWRITE_QUERY,
                        ExecutionOperation.KEYWORD_SEARCH,
                        ExecutionOperation.RETURN_RESULTS
                );
    }
}

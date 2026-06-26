package io.qwenbridge.execution;

import io.qwenbridge.decision.SearchDecision;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultExecutionEngineTest {

    private final ExecutionPlanFactory factory = new ExecutionPlanFactory();
    private final ExecutionEngine engine = new DefaultExecutionEngine();

    @Test
    void shouldExecuteKeywordPlan() {

        ExecutionPlan plan = factory.from(SearchDecision.keyword());

        ExecutionResult result = engine.execute(plan);

        assertThat(result.executed()).isTrue();

        assertThat(result.operations())
                .contains(
                        ExecutionOperation.KEYWORD_SEARCH,
                        ExecutionOperation.RETURN_RESULTS
                );

        assertThat(result.results()).isEmpty();
    }

    @Test
    void shouldExecuteDirectAnswerPlan() {

        ExecutionPlan plan = factory.from(SearchDecision.directAnswer());

        ExecutionResult result = engine.execute(plan);

        assertThat(result.executed()).isTrue();

        assertThat(result.operations())
                .containsExactly(
                        ExecutionOperation.DIRECT_ANSWER
                );
    }
}

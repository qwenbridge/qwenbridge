package io.qwenbridge.execution;

import io.qwenbridge.decision.SearchDecision;
import io.qwenbridge.execution.executor.DirectAnswerExecutor;
import io.qwenbridge.execution.executor.KeywordSearchExecutor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultExecutionEngineTest {

    private final ExecutionPlanFactory factory = new ExecutionPlanFactory();

    private final ExecutionEngine engine =
            new DefaultExecutionEngine(
                    List.of(
                            new KeywordSearchExecutor(),
                            new DirectAnswerExecutor()
                    )
            );

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

        assertThat(result.results())
                .containsExactly("keyword-search-placeholder-result");
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

    @Test
    void shouldIgnoreOperationsWithoutRegisteredExecutor() {

        ExecutionPlan plan =
                new ExecutionPlan(
                        io.qwenbridge.decision.SearchMode.KEYWORD,
                        io.qwenbridge.decision.SearchBackend.IN_MEMORY,
                        List.of(
                                new ExecutionStep(
                                        1,
                                        ExecutionOperation.RETURN_RESULTS,
                                        "return"
                                )
                        ),
                        "test"
                );

        ExecutionResult result = engine.execute(plan);
        assertThat(result.executed()).isTrue();
        assertThat(result.results()).isEmpty();
    }
}

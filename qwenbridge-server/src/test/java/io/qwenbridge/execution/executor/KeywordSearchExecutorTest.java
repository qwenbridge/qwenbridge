package io.qwenbridge.execution.executor;

import io.qwenbridge.execution.ExecutionOperation;
import io.qwenbridge.execution.ExecutionStep;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordSearchExecutorTest {

    @Test
    void shouldExecuteKeywordSearchOperation() {
        KeywordSearchExecutor executor = new KeywordSearchExecutor();

        assertThat(executor.operation()).isEqualTo(ExecutionOperation.KEYWORD_SEARCH);

        assertThat(executor.execute(
                new ExecutionStep(10, ExecutionOperation.KEYWORD_SEARCH, "keyword")
        )).containsExactly("keyword-search-placeholder-result");
    }
}
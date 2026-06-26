package io.qwenbridge.execution.executor;

import io.qwenbridge.execution.ExecutionOperation;
import io.qwenbridge.execution.ExecutionStep;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VectorSearchExecutorTest {

    @Test
    void shouldExecuteVectorSearchOperation() {
        VectorSearchExecutor executor = new VectorSearchExecutor();

        assertThat(executor.operation()).isEqualTo(ExecutionOperation.VECTOR_SEARCH);

        assertThat(executor.execute(
                new ExecutionStep(10, ExecutionOperation.VECTOR_SEARCH, "vector")
        )).containsExactly("vector-search-placeholder-result");
    }
}

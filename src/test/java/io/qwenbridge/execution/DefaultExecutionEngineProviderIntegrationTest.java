package io.qwenbridge.execution;

import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchMode;
import io.qwenbridge.execution.executor.ExecutionOperationExecutor;
import io.qwenbridge.execution.provider.implementation.InMemorySearchProvider;
import io.qwenbridge.execution.provider.registry.DefaultSearchProviderRegistry;
import io.qwenbridge.execution.provider.resolver.DefaultSearchProviderResolver;
import io.qwenbridge.execution.provider.spi.SearchProviderResolver;
import io.qwenbridge.pipeline.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultExecutionEngineProviderIntegrationTest {

    @Test
    void shouldExecuteUsingDefaultSearchProvider() {
        SearchProviderResolver resolver =
                new DefaultSearchProviderResolver(
                        new DefaultSearchProviderRegistry(
                                List.of(new InMemorySearchProvider())
                        )
                );

        DefaultExecutionEngine engine =
                new DefaultExecutionEngine(List.<ExecutionOperationExecutor>of(), resolver);

        ExecutionPlan plan = new ExecutionPlan(
                SearchMode.KEYWORD,
                SearchBackend.IN_MEMORY,
                List.of(
                        new ExecutionStep(
                                1,
                                ExecutionOperation.KEYWORD_SEARCH,
                                "keyword search"
                        )
                ),
                "keyword search plan"
        );

        ExecutionResult result =
                engine.execute(plan, new ExecutionContext("iphone"));

        assertThat(result.executed()).isTrue();
        assertThat(result.operations()).containsExactly(ExecutionOperation.KEYWORD_SEARCH);
        assertThat(result.results()).hasSize(1);
        assertThat(result.results().getFirst()).contains("iPhone 16 Pro");
        assertThat(result.reason())
                .isEqualTo("Execution plan executed successfully using search provider: inmemory");
    }
}
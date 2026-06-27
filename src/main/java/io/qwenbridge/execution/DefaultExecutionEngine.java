package io.qwenbridge.execution;

import io.qwenbridge.execution.executor.ExecutionOperationExecutor;
import io.qwenbridge.execution.provider.implementation.InMemorySearchProvider;
import io.qwenbridge.execution.provider.model.SearchRequest;
import io.qwenbridge.execution.provider.model.SearchRequestFactory;
import io.qwenbridge.execution.provider.model.SearchResponse;
import io.qwenbridge.execution.provider.registry.DefaultSearchProviderRegistry;
import io.qwenbridge.execution.provider.resolver.DefaultSearchProviderResolver;
import io.qwenbridge.execution.provider.spi.SearchProvider;
import io.qwenbridge.execution.provider.spi.SearchProviderResolver;
import io.qwenbridge.pipeline.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DefaultExecutionEngine implements ExecutionEngine {

    private final Map<ExecutionOperation, ExecutionOperationExecutor> executors;
    private final SearchProviderResolver searchProviderResolver;

    public DefaultExecutionEngine(List<ExecutionOperationExecutor> executors) {
        this(
                executors,
                new DefaultSearchProviderResolver(
                        new DefaultSearchProviderRegistry(
                                List.of(new InMemorySearchProvider())
                        )
                )
        );
    }

    @Autowired
    public DefaultExecutionEngine(
            List<ExecutionOperationExecutor> executors,
            SearchProviderResolver searchProviderResolver
    ) {
        this.executors = new HashMap<>();
        this.searchProviderResolver = searchProviderResolver;

        for (ExecutionOperationExecutor executor : executors) {
            this.executors.put(executor.operation(), executor);
        }
    }

    @Override
    public ExecutionResult execute(ExecutionPlan plan) {
        List<String> results =
                plan.steps()
                        .stream()
                        .filter(step -> executors.containsKey(step.operation()))
                        .flatMap(step ->
                                executors.get(step.operation())
                                        .execute(step)
                                        .stream()
                        )
                        .toList();

        return ExecutionResult.completed(
                plan.steps()
                        .stream()
                        .map(ExecutionStep::operation)
                        .toList(),
                results,
                "Execution plan executed successfully."
        );
    }

    @Override
    public ExecutionResult execute(ExecutionPlan plan, ExecutionContext context) {
        SearchRequest searchRequest = SearchRequestFactory.from(context);
        SearchProvider provider = searchProviderResolver.resolve(context);
        SearchResponse response = provider.search(searchRequest);
        context.store(SearchResponse.class, response);

        List<String> results = response.results()
                .hits()
                .stream()
                .map(hit -> hit.document().toString())
                .toList();

        return ExecutionResult.completed(
                plan.steps()
                        .stream()
                        .map(ExecutionStep::operation)
                        .toList(),
                results,
                "Execution plan executed successfully using search provider: " + provider.name()
        );
    }
}
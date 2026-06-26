package io.qwenbridge.execution;

import io.qwenbridge.execution.executor.ExecutionOperationExecutor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DefaultExecutionEngine implements ExecutionEngine {

    private final Map<ExecutionOperation, ExecutionOperationExecutor> executors;

    public DefaultExecutionEngine(List<ExecutionOperationExecutor> executors) {

        this.executors = new HashMap<>();

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
}
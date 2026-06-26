package io.qwenbridge.execution;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DefaultExecutionEngine implements ExecutionEngine {

    @Override
    public ExecutionResult execute(ExecutionPlan plan) {

        List<ExecutionOperation> operations =
                plan.steps()
                        .stream()
                        .map(ExecutionStep::operation)
                        .toList();

        return ExecutionResult.completed(
                operations,
                List.of(),
                "Execution plan executed successfully."
        );
    }
}

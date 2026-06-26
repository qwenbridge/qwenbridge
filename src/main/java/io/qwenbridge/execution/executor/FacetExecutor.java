package io.qwenbridge.execution.executor;

import io.qwenbridge.execution.ExecutionOperation;
import io.qwenbridge.execution.ExecutionStep;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FacetExecutor implements ExecutionOperationExecutor {

    @Override
    public ExecutionOperation operation() {
        return ExecutionOperation.APPLY_FACETS;
    }

    @Override
    public List<String> execute(ExecutionStep step) {
        return List.of("facet-placeholder-result");
    }
}

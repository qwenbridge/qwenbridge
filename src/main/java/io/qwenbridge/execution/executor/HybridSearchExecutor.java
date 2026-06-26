package io.qwenbridge.execution.executor;

import io.qwenbridge.execution.ExecutionOperation;
import io.qwenbridge.execution.ExecutionStep;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HybridSearchExecutor implements ExecutionOperationExecutor {

    @Override
    public ExecutionOperation operation() {
        return ExecutionOperation.HYBRID_SEARCH;
    }

    @Override
    public List<String> execute(ExecutionStep step) {
        return List.of("hybrid-search-placeholder-result");
    }
}

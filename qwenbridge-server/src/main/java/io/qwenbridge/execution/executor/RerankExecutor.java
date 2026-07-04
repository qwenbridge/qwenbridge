package io.qwenbridge.execution.executor;

import io.qwenbridge.execution.ExecutionOperation;
import io.qwenbridge.execution.ExecutionStep;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RerankExecutor implements ExecutionOperationExecutor {

    @Override
    public ExecutionOperation operation() {
        return ExecutionOperation.RERANK_RESULTS;
    }

    @Override
    public List<String> execute(ExecutionStep step) {
        return List.of("rerank-placeholder-result");
    }
}

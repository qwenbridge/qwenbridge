package io.qwenbridge.execution.executor;

import io.qwenbridge.execution.ExecutionOperation;
import io.qwenbridge.execution.ExecutionStep;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class VectorSearchExecutor implements ExecutionOperationExecutor {

  @Override
  public ExecutionOperation operation() {
    return ExecutionOperation.VECTOR_SEARCH;
  }

  @Override
  public List<String> execute(ExecutionStep step) {
    return List.of("vector-search-placeholder-result");
  }
}

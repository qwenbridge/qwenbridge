package io.qwenbridge.execution.executor;

import io.qwenbridge.execution.ExecutionOperation;
import io.qwenbridge.execution.ExecutionStep;
import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
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

package io.qwenbridge.execution.executor;

import io.qwenbridge.execution.ExecutionOperation;
import io.qwenbridge.execution.ExecutionStep;
import java.util.List;
import org.springframework.stereotype.Component;

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

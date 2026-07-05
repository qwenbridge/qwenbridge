package io.qwenbridge.execution.executor;

import io.qwenbridge.execution.ExecutionOperation;
import io.qwenbridge.execution.ExecutionStep;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ReturnResultsExecutor implements ExecutionOperationExecutor {

  @Override
  public ExecutionOperation operation() {
    return ExecutionOperation.RETURN_RESULTS;
  }

  @Override
  public List<String> execute(ExecutionStep step) {
    return List.of("return-results-placeholder");
  }
}

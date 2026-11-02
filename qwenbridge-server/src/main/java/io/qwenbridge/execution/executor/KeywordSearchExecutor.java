package io.qwenbridge.execution.executor;

import io.qwenbridge.execution.ExecutionOperation;
import io.qwenbridge.execution.ExecutionStep;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class KeywordSearchExecutor implements ExecutionOperationExecutor {

  @Override
  public ExecutionOperation operation() {
    return ExecutionOperation.KEYWORD_SEARCH;
  }

  @Override
  public List<String> execute(ExecutionStep step) {
    return List.of("keyword-search-placeholder-result");
  }
}

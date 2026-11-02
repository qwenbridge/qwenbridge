package io.qwenbridge.execution.executor;

import io.qwenbridge.execution.ExecutionOperation;
import io.qwenbridge.execution.ExecutionStep;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class DirectAnswerExecutor implements ExecutionOperationExecutor {

  @Override
  public ExecutionOperation operation() {
    return ExecutionOperation.DIRECT_ANSWER;
  }

  @Override
  public List<String> execute(ExecutionStep step) {
    return List.of("direct-answer-placeholder-result");
  }
}

package io.qwenbridge.execution.executor;

import io.qwenbridge.execution.ExecutionOperation;
import io.qwenbridge.execution.ExecutionStep;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
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

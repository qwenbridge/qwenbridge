package io.qwenbridge.execution.executor;

import io.qwenbridge.execution.ExecutionOperation;
import io.qwenbridge.execution.ExecutionStep;

import java.util.List;

public interface ExecutionOperationExecutor {

    ExecutionOperation operation();

    List<String> execute(ExecutionStep step);
}
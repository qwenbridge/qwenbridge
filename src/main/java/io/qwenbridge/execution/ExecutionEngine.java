package io.qwenbridge.execution;

import io.qwenbridge.pipeline.ExecutionContext;

public interface ExecutionEngine {

    ExecutionResult execute(ExecutionPlan plan);
    ExecutionResult execute(ExecutionPlan plan, ExecutionContext context);

}
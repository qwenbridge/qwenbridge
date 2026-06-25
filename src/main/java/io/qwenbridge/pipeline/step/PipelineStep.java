package io.qwenbridge.pipeline.step;

import io.qwenbridge.pipeline.ExecutionContext;

public interface PipelineStep<T> {
    String name();
    int order();
    Class<T> resultType();
    T execute(ExecutionContext context);
}

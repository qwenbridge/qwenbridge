package io.omnisearch.pipeline.step;

import io.omnisearch.pipeline.ExecutionContext;

public interface PipelineStep<T> {
    String name();
    int order();
    Class<T> resultType();
    T execute(ExecutionContext context);
}

package io.qwenbridge.pipeline.step;

import io.qwenbridge.event.model.PipelineStage;
import io.qwenbridge.pipeline.ExecutionContext;

public interface PipelineStep<T> {

  String name();

  PipelineStage stage();

  int order();

  Class<T> resultType();

  T execute(ExecutionContext context);

  default boolean publishEvents() {
    return true;
  }
}

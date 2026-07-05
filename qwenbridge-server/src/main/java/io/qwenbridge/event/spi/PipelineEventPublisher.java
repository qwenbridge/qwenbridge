package io.qwenbridge.event.spi;

import io.qwenbridge.event.model.PipelineEvent;

public interface PipelineEventPublisher {

  void publish(PipelineEvent<?> event);

  default boolean isEnabled() {
    return true;
  }
}

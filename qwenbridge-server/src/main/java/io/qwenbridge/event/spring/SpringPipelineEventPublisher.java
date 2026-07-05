package io.qwenbridge.event.spring;

import io.qwenbridge.event.model.PipelineEvent;
import io.qwenbridge.event.spi.PipelineEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@RequiredArgsConstructor
public class SpringPipelineEventPublisher implements PipelineEventPublisher {

  private final ApplicationEventPublisher applicationEventPublisher;

  @Override
  public void publish(PipelineEvent<?> event) {
    applicationEventPublisher.publishEvent(event);
  }
}

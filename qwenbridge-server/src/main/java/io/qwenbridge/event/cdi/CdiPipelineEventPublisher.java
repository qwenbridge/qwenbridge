package io.qwenbridge.event.cdi;

import io.qwenbridge.event.model.PipelineEvent;
import io.qwenbridge.event.spi.PipelineEventPublisher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

@ApplicationScoped
public class CdiPipelineEventPublisher implements PipelineEventPublisher {

  private final Event<PipelineEvent<?>> events;

  @Inject
  public CdiPipelineEventPublisher(Event<PipelineEvent<?>> events) {
    this.events = events;
  }

  @Override
  public void publish(PipelineEvent<?> event) {
    events.fire(event);
  }
}

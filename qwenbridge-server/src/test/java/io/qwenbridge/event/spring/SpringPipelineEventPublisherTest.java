package io.qwenbridge.event.spring;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.qwenbridge.event.model.PipelineEvent;
import io.qwenbridge.event.model.PipelineEvents;
import io.qwenbridge.event.model.PipelineStage;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.Test;

class SpringPipelineEventPublisherTest {

  @Test
  void shouldPublishCdiEvent() {
    @SuppressWarnings("unchecked")
    Event<PipelineEvent<?>> publisher = (Event<PipelineEvent<?>>) mock(Event.class);

    SpringPipelineEventPublisher eventPublisher = new SpringPipelineEventPublisher(publisher);

    var event = PipelineEvents.info(PipelineStage.PIPELINE, "hello");

    eventPublisher.publish(event);

    verify(publisher).fire(event);
  }
}

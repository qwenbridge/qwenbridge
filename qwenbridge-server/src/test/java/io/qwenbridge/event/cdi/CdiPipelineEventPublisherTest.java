package io.qwenbridge.event.cdi;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.qwenbridge.event.model.PipelineEvent;
import io.qwenbridge.event.model.PipelineEvents;
import io.qwenbridge.event.model.PipelineStage;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.Test;

class CdiPipelineEventPublisherTest {

  @Test
  void shouldPublishCdiEvent() {
    @SuppressWarnings("unchecked")
    Event<PipelineEvent<?>> publisher = (Event<PipelineEvent<?>>) mock(Event.class);

    CdiPipelineEventPublisher eventPublisher = new CdiPipelineEventPublisher(publisher);

    var event = PipelineEvents.info(PipelineStage.PIPELINE, "hello");

    eventPublisher.publish(event);

    verify(publisher).fire(event);
  }
}

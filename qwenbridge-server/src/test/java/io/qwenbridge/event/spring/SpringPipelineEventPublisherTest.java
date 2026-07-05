package io.qwenbridge.event.spring;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.qwenbridge.event.model.PipelineEvents;
import io.qwenbridge.event.model.PipelineStage;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class SpringPipelineEventPublisherTest {

  @Test
  void shouldPublishSpringApplicationEvent() {
    ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    SpringPipelineEventPublisher eventPublisher = new SpringPipelineEventPublisher(publisher);

    var event = PipelineEvents.info(PipelineStage.PIPELINE, "hello");

    eventPublisher.publish(event);

    verify(publisher).publishEvent(event);
  }
}

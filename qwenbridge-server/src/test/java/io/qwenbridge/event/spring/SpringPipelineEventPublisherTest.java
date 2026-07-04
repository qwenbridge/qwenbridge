package io.qwenbridge.event.spring;

import io.qwenbridge.event.model.PipelineEvents;
import io.qwenbridge.event.model.PipelineStage;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SpringPipelineEventPublisherTest {

    @Test
    void shouldPublishSpringApplicationEvent() {
        ApplicationEventPublisher publisher =
                mock(ApplicationEventPublisher.class);

        SpringPipelineEventPublisher eventPublisher =
                new SpringPipelineEventPublisher(publisher);

        var event = PipelineEvents.info(
                PipelineStage.PIPELINE,
                "hello"
        );

        eventPublisher.publish(event);

        verify(publisher).publishEvent(event);
    }
}

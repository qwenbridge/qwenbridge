package io.qwenbridge.event.noop;

import io.qwenbridge.event.model.PipelineEvent;
import io.qwenbridge.event.spi.PipelineEventPublisher;

public class NoOpPipelineEventPublisher
        implements PipelineEventPublisher {

    @Override
    public void publish(PipelineEvent<?> event) {
        // No operation.
    }
}

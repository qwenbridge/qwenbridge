package io.qwenbridge.streaming.event;

import io.qwenbridge.event.model.PipelineEvent;
import org.springframework.stereotype.Component;

@Component
public class PipelineStreamingEventMapper {

    public <T> PipelineStreamingEvent<T> map(PipelineEvent<T> event) {

        return new PipelineStreamingEvent<>(
                event.id().value().toString(),
                event.timestamp(),
                event.stage(),
                event.type(),
                event.payload()
        );
    }
}

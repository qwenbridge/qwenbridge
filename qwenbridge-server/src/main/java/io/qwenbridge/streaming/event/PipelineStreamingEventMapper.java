package io.qwenbridge.streaming.event;

import io.qwenbridge.event.model.PipelineEvent;
import io.qwenbridge.event.model.PipelineEventMetadata;
import io.qwenbridge.event.snapshot.PipelineContextSnapshot;
import org.springframework.stereotype.Component;

@Component
public class PipelineStreamingEventMapper {

    public PipelineStreamingEvent map(PipelineEvent<?> event) {
        PipelineEventMetadata metadata = event.metadata();

        return new PipelineStreamingEvent(
                event.id().value().toString(),
                event.timestamp(),
                resolveRequestId(event),
                eventName(event),
                event.stage().name().toLowerCase(),
                event.type().name().toLowerCase(),
                metadata.producer(),
                metadata.sequenceNumber(),
                mapPayload(event.payload())
        );
    }

    private String eventName(PipelineEvent<?> event) {
        return event.stage().name().toLowerCase()
                + "."
                + event.type().name().toLowerCase();
    }

    private String resolveRequestId(PipelineEvent<?> event) {
        if (event.metadata() != null && !event.metadata().requestId().isBlank()) {
            return event.metadata().requestId();
        }

        if (event.payload() instanceof PipelineContextSnapshot snapshot) {
            return snapshot.requestId();
        }

        return "";
    }

    private StreamingPayload mapPayload(Object payload) {
        if (payload instanceof PipelineContextSnapshot snapshot) {
            return SnapshotStreamingPayload.from(snapshot);
        }

        return null;
    }
}

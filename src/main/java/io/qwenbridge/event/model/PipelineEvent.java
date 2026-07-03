package io.qwenbridge.event.model;

import io.qwenbridge.event.value.PipelineEventId;

import java.time.Instant;

public record PipelineEvent<T>(

        PipelineEventId id,
        Instant timestamp,
        PipelineStage stage,
        PipelineEventType type,
        PipelineEventMetadata metadata,
        Class<T> payloadType,
        T payload

) {

    public PipelineEvent {

        id = id == null
                ? PipelineEventId.generate()
                : id;

        timestamp = timestamp == null
                ? Instant.now()
                : timestamp;

        metadata = metadata == null
                ? PipelineEventMetadata.empty()
                : metadata;
    }

}

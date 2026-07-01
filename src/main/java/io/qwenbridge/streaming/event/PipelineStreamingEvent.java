package io.qwenbridge.streaming.event;

import io.qwenbridge.event.model.PipelineEventType;
import io.qwenbridge.event.model.PipelineStage;

import java.time.Instant;

public record PipelineStreamingEvent<T>(

        String id,
        Instant timestamp,
        PipelineStage stage,
        PipelineEventType type,
        T payload

) {
}
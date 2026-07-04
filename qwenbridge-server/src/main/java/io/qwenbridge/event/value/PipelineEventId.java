package io.qwenbridge.event.value;

import java.util.UUID;

public record PipelineEventId(UUID value) {

    public PipelineEventId {
        value = value == null ? UUID.randomUUID() : value;
    }

    public static PipelineEventId generate() {
        return new PipelineEventId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

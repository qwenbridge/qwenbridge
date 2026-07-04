package io.qwenbridge.streaming.event;

import java.time.Instant;

public record PipelineStreamingEvent(

        String id,
        Instant timestamp,
        String requestId,
        String event,
        String stage,
        String type,
        String producer,
        long sequenceNumber,
        StreamingPayload payload

) {
}

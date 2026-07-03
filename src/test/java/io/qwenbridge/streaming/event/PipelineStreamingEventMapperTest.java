package io.qwenbridge.streaming.event;

import io.qwenbridge.event.model.PipelineEvent;
import io.qwenbridge.event.model.PipelineEventMetadata;
import io.qwenbridge.event.model.PipelineEvents;
import io.qwenbridge.event.model.PipelineStage;
import io.qwenbridge.event.snapshot.PipelineContextSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PipelineStreamingEventMapperTest {

    private final PipelineStreamingEventMapper mapper =
            new PipelineStreamingEventMapper();

    @Test
    void shouldMapPipelineEventToPublicStreamingEnvelope() {
        PipelineContextSnapshot snapshot =
                new PipelineContextSnapshot(
                        "request-1",
                        "desk",
                        false,
                        true,
                        "en",
                        "SEARCH",
                        "ALLOW",
                        123456789L
                );

        PipelineEvent<PipelineContextSnapshot> event =
                PipelineEvents.stepStarted(
                        PipelineStage.INTENT,
                        snapshot,
                        PipelineEventMetadata.of("request-1", 7L)
                );

        PipelineStreamingEvent streaming = mapper.map(event);

        assertEquals(event.id().value().toString(), streaming.id());
        assertEquals(event.timestamp(), streaming.timestamp());
        assertEquals("request-1", streaming.requestId());
        assertEquals("intent.started", streaming.event());
        assertEquals("intent", streaming.stage());
        assertEquals("started", streaming.type());
        assertEquals("qwenbridge", streaming.producer());
        assertEquals(7L, streaming.sequenceNumber());
        assertInstanceOf(SnapshotStreamingPayload.class, streaming.payload());

        SnapshotStreamingPayload payload =
                (SnapshotStreamingPayload) streaming.payload();

        assertEquals("desk", payload.query());
        assertFalse(payload.stopped());
        assertTrue(payload.safe());
        assertEquals("en", payload.language());
        assertEquals("SEARCH", payload.intent());
        assertEquals("ALLOW", payload.decision());
        assertEquals(123456789L, payload.timestamp());
    }
}
package io.qwenbridge.streaming.event;

import io.qwenbridge.event.model.PipelineEvent;
import io.qwenbridge.event.model.PipelineEvents;
import io.qwenbridge.event.model.PipelineStage;
import io.qwenbridge.event.snapshot.PipelineContextSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PipelineStreamingEventMapperTest {

    private final PipelineStreamingEventMapper mapper =
            new PipelineStreamingEventMapper();

    @Test
    void shouldMapPipelineEvent() {

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
                        snapshot
                );

        PipelineStreamingEvent<PipelineContextSnapshot> streaming =
                mapper.map(event);

        assertEquals(event.id().value().toString(), streaming.id());
        assertEquals(event.timestamp(), streaming.timestamp());
        assertEquals(event.stage(), streaming.stage());
        assertEquals(event.type(), streaming.type());
        assertSame(snapshot, streaming.payload());
    }
}

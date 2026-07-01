package io.qwenbridge.streaming.listener;

import io.qwenbridge.event.model.PipelineEvents;
import io.qwenbridge.event.model.PipelineStage;
import io.qwenbridge.streaming.event.PipelineStreamingEventMapper;
import io.qwenbridge.streaming.session.StreamingSessionRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SsePipelineEventListenerTest {

    @Test
    void shouldListenToPipelineEventsWithoutFailingWhenNoSessionsExist() {
        StreamingSessionRegistry registry = new StreamingSessionRegistry();
        PipelineStreamingEventMapper mapper = new PipelineStreamingEventMapper();

        SsePipelineEventListener listener =
                new SsePipelineEventListener(registry, mapper);

        listener.onPipelineEvent(
                PipelineEvents.info(PipelineStage.PIPELINE, "hello")
        );

        assertThat(registry.size()).isZero();
    }
}

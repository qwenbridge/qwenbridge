package io.qwenbridge.streaming.listener;

import io.qwenbridge.event.model.PipelineEventMetadata;
import io.qwenbridge.event.model.PipelineEvents;
import io.qwenbridge.event.snapshot.PipelineContextSnapshot;
import io.qwenbridge.streaming.config.StreamingProperties;
import io.qwenbridge.streaming.event.PipelineEventTerminalPolicy;
import io.qwenbridge.streaming.event.PipelineStreamingEventMapper;
import io.qwenbridge.streaming.session.StreamingSessionRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SsePipelineEventListenerTerminalLifecycleTest {

    @Test
    void shouldCompleteAndRemoveSessionWhenPipelineCompletes() {
        StreamingSessionRegistry registry =
                new StreamingSessionRegistry(new StreamingProperties(30_000));

        SsePipelineEventListener listener =
                new SsePipelineEventListener(
                        registry,
                        new PipelineStreamingEventMapper(),
                        new PipelineEventTerminalPolicy()
                );

        String requestId = "request-123";

        PipelineContextSnapshot snapshot =
                new PipelineContextSnapshot(
                        requestId,
                        "desk",
                        false,
                        true,
                        "en",
                        "SEARCH",
                        "ALLOW",
                        123456789L
                );

        registry.register(requestId);

        listener.onPipelineEvent(
                PipelineEvents.pipelineCompleted(
                        snapshot,
                        PipelineEventMetadata.of(requestId, 99L)
                )
        );

        assertThat(registry.findByRequestId(requestId)).isEmpty();
    }
}
package io.qwenbridge.streaming.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.qwenbridge.event.model.PipelineEventMetadata;
import io.qwenbridge.event.model.PipelineEvents;
import io.qwenbridge.event.snapshot.PipelineContextSnapshot;
import io.qwenbridge.operations.metrics.OperationsMetrics;
import io.qwenbridge.streaming.config.StreamingProperties;
import io.qwenbridge.streaming.event.PipelineEventTerminalPolicy;
import io.qwenbridge.streaming.event.PipelineStreamingEventMapper;
import io.qwenbridge.streaming.session.StreamingSessionRegistry;
import org.junit.jupiter.api.Test;

class SsePipelineEventListenerTerminalLifecycleTest {

  @Test
  void shouldCompleteAndRemoveSessionWhenPipelineCompletes() {
    StreamingSessionRegistry registry =
        new StreamingSessionRegistry(
            new StreamingProperties(30_000, java.time.Duration.ofSeconds(30), 1_000L, 1_100L),
            mock(OperationsMetrics.class));

    SsePipelineEventListener listener =
        new SsePipelineEventListener(
            registry, new PipelineStreamingEventMapper(), new PipelineEventTerminalPolicy());

    String requestId = "request-123";

    PipelineContextSnapshot snapshot =
        new PipelineContextSnapshot(
            requestId, "desk", false, true, "en", "SEARCH", "ALLOW", 123456789L);

    registry.register(requestId);

    listener.onPipelineEvent(
        PipelineEvents.pipelineCompleted(snapshot, PipelineEventMetadata.of(requestId, 99L)));

    assertThat(registry.findByRequestId(requestId)).isEmpty();
  }

  @Test
  void shouldRemoveSessionWhenPipelineFailsAfterFailureEvent() {
    StreamingSessionRegistry registry =
        new StreamingSessionRegistry(
            new StreamingProperties(30_000, java.time.Duration.ofSeconds(30), 1_000L, 1_100L),
            mock(OperationsMetrics.class));

    SsePipelineEventListener listener =
        new SsePipelineEventListener(
            registry, new PipelineStreamingEventMapper(), new PipelineEventTerminalPolicy());

    String requestId = "request-failed-123";

    PipelineContextSnapshot snapshot =
        new PipelineContextSnapshot(
            requestId, "desk", false, true, "en", "SEARCH", "ALLOW", 123456789L);

    registry.register(requestId);

    listener.onPipelineEvent(
        PipelineEvents.pipelineFailed(snapshot, PipelineEventMetadata.of(requestId, 100L)));

    assertThat(registry.findByRequestId(requestId)).isEmpty();
  }
}

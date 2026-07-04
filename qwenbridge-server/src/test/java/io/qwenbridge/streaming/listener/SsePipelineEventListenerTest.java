package io.qwenbridge.streaming.listener;

import io.qwenbridge.event.model.PipelineEventMetadata;
import io.qwenbridge.event.model.PipelineEvents;
import io.qwenbridge.event.model.PipelineStage;
import io.qwenbridge.event.snapshot.PipelineContextSnapshot;
import io.qwenbridge.streaming.event.FailureStreamingPayload;
import io.qwenbridge.streaming.event.PipelineStreamingEvent;
import io.qwenbridge.streaming.event.PipelineStreamingEventMapper;
import io.qwenbridge.streaming.session.StreamingSessionRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.qwenbridge.streaming.event.PipelineEventTerminalPolicy;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SsePipelineEventListenerTest {

    @Test
    void shouldRouteMappedEventToMatchingRequestId() {
        StreamingSessionRegistry registry =
                mock(StreamingSessionRegistry.class);

        PipelineStreamingEventMapper mapper =
                new PipelineStreamingEventMapper();

        SsePipelineEventListener listener =
                new SsePipelineEventListener(
                        registry,
                        mapper,
                        new PipelineEventTerminalPolicy()
                );

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

        var event =
                PipelineEvents.stepStarted(
                        PipelineStage.INTENT,
                        snapshot,
                        PipelineEventMetadata.of("request-1", 7L)
                );

        listener.onPipelineEvent(event);

        ArgumentCaptor<PipelineStreamingEvent> payloadCaptor =
                ArgumentCaptor.forClass(PipelineStreamingEvent.class);

        verify(registry).sendToRequest(
                eq("request-1"),
                eq(event.id().value().toString()),
                eq("intent.started"),
                payloadCaptor.capture()
        );

        PipelineStreamingEvent payload = payloadCaptor.getValue();

        assertThat(payload.requestId()).isEqualTo("request-1");
        assertThat(payload.event()).isEqualTo("intent.started");
        assertThat(payload.stage()).isEqualTo("intent");
        assertThat(payload.type()).isEqualTo("started");
        assertThat(payload.sequenceNumber()).isEqualTo(7L);
    }

    @Test
    void shouldIgnoreEventsWithoutRequestId() {
        StreamingSessionRegistry registry =
                mock(StreamingSessionRegistry.class);

        PipelineStreamingEventMapper mapper =
                new PipelineStreamingEventMapper();

        SsePipelineEventListener listener =
                new SsePipelineEventListener(
                        registry,
                        mapper,
                        new PipelineEventTerminalPolicy()
                );

        listener.onPipelineEvent(
                PipelineEvents.info(PipelineStage.PIPELINE, "hello")
        );

        verifyNoInteractions(registry);
    }

    @Test
    void shouldCompleteMatchingRequestAfterTerminalPipelineEvent() {
        StreamingSessionRegistry registry =
                mock(StreamingSessionRegistry.class);

        PipelineStreamingEventMapper mapper =
                new PipelineStreamingEventMapper();

        SsePipelineEventListener listener =
                new SsePipelineEventListener(
                        registry,
                        mapper,
                        new PipelineEventTerminalPolicy()
                );

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

        var event =
                PipelineEvents.pipelineCompleted(
                        snapshot,
                        PipelineEventMetadata.of("request-1", 99L)
                );

        listener.onPipelineEvent(event);

        InOrder inOrder = inOrder(registry);

        inOrder.verify(registry).sendToRequest(
                eq("request-1"),
                eq(event.id().value().toString()),
                eq("pipeline.completed"),
                any(PipelineStreamingEvent.class)
        );

        inOrder.verify(registry).completeRequest("request-1");
    }

    @Test
    void shouldNotCompleteRequestAfterNonTerminalStepEvent() {
        StreamingSessionRegistry registry =
                mock(StreamingSessionRegistry.class);

        PipelineStreamingEventMapper mapper =
                new PipelineStreamingEventMapper();

        SsePipelineEventListener listener =
                new SsePipelineEventListener(
                        registry,
                        mapper,
                        new PipelineEventTerminalPolicy()
                );

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

        var event =
                PipelineEvents.stepCompleted(
                        PipelineStage.SEARCH,
                        snapshot,
                        PipelineEventMetadata.of("request-1", 8L)
                );

        listener.onPipelineEvent(event);

        verify(registry, never()).completeRequest(anyString());
    }

    @Test
    void shouldPublishStableFailureEventAndCloseRequestWhenPipelineFails() {
        StreamingSessionRegistry registry =
                mock(StreamingSessionRegistry.class);

        PipelineStreamingEventMapper mapper =
                new PipelineStreamingEventMapper();

        SsePipelineEventListener listener =
                new SsePipelineEventListener(
                        registry,
                        mapper,
                        new PipelineEventTerminalPolicy()
                );

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

        var event =
                PipelineEvents.pipelineFailed(
                        snapshot,
                        PipelineEventMetadata.of("request-1", 100L)
                );

        listener.onPipelineEvent(event);

        ArgumentCaptor<FailureStreamingPayload> payloadCaptor =
                ArgumentCaptor.forClass(FailureStreamingPayload.class);

        verify(registry).failRequest(
                eq("request-1"),
                eq(event.id().value().toString()),
                eq("stream.failure"),
                payloadCaptor.capture()
        );

        FailureStreamingPayload payload = payloadCaptor.getValue();

        assertThat(payload.requestId()).isEqualTo("request-1");
        assertThat(payload.code()).isEqualTo("PIPELINE_FAILED");
        assertThat(payload.message()).isEqualTo("Pipeline failed before completion");
        assertThat(payload.terminal()).isTrue();

        verify(registry, never()).sendToRequest(anyString(), anyString(), anyString(), any());
        verify(registry, never()).completeRequest(anyString());
    }

}

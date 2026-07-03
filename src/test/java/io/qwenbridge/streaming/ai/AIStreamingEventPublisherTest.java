package io.qwenbridge.streaming.ai;

import io.qwenbridge.streaming.event.AICompletedStreamingPayload;
import io.qwenbridge.streaming.event.AIFailedStreamingPayload;
import io.qwenbridge.streaming.event.AITokenStreamingPayload;
import io.qwenbridge.streaming.session.StreamingSessionRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AIStreamingEventPublisherTest {

    private final StreamingSessionRegistry registry = mock(StreamingSessionRegistry.class);
    private final AIStreamingEventPublisher publisher = new AIStreamingEventPublisher(registry);

    @Test
    void shouldPublishTokenEvent() {
        publisher.token("request-1", 1L, "hello");

        ArgumentCaptor<AITokenStreamingPayload> payload =
                ArgumentCaptor.forClass(AITokenStreamingPayload.class);

        verify(registry).sendToRequest(
                eq("request-1"),
                anyString(),
                eq("ai.token"),
                payload.capture()
        );

        assertThat(payload.getValue().requestId()).isEqualTo("request-1");
        assertThat(payload.getValue().tokenIndex()).isEqualTo(1L);
        assertThat(payload.getValue().content()).isEqualTo("hello");
        assertThat(payload.getValue().terminal()).isFalse();
    }

    @Test
    void shouldPublishCompletedEventWithoutClosingSseSession() {
        publisher.completed("request-1", 3L);

        ArgumentCaptor<AICompletedStreamingPayload> payload =
                ArgumentCaptor.forClass(AICompletedStreamingPayload.class);

        verify(registry).sendToRequest(
                eq("request-1"),
                anyString(),
                eq("ai.completed"),
                payload.capture()
        );

        assertThat(payload.getValue().requestId()).isEqualTo("request-1");
        assertThat(payload.getValue().tokenCount()).isEqualTo(3L);
        assertThat(payload.getValue().terminal()).isFalse();

        verify(registry, never()).completeRequest(anyString());
        verify(registry, never()).failRequest(anyString(), anyString(), anyString(), any());
    }

    @Test
    void shouldPublishFailedEventWithoutClosingSseSession() {
        publisher.failed("request-1", "AI_STREAM_FAILED", "provider failed");

        ArgumentCaptor<AIFailedStreamingPayload> payload =
                ArgumentCaptor.forClass(AIFailedStreamingPayload.class);

        verify(registry).sendToRequest(
                eq("request-1"),
                anyString(),
                eq("ai.failed"),
                payload.capture()
        );

        assertThat(payload.getValue().requestId()).isEqualTo("request-1");
        assertThat(payload.getValue().code()).isEqualTo("AI_STREAM_FAILED");
        assertThat(payload.getValue().message()).isEqualTo("provider failed");
        assertThat(payload.getValue().terminal()).isFalse();

        verify(registry, never()).completeRequest(anyString());
        verify(registry, never()).failRequest(anyString(), anyString(), anyString(), any());
    }

    @Test
    void shouldIgnoreBlankRequestIdAndBlankToken() {
        publisher.token("", 1L, "hello");
        publisher.token("request-1", 1L, "");
        publisher.completed("", 0L);
        publisher.failed("", "CODE", "message");

        verifyNoInteractions(registry);
    }
}

package io.qwenbridge.streaming.session;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingSessionRegistryTest {

    @Test
    void shouldRegisterSessionForRequestId() {
        StreamingSessionRegistry registry = new StreamingSessionRegistry();

        StreamingSession session = registry.register("request-1");

        assertThat(session.sessionId()).isNotBlank();
        assertThat(session.requestId()).isEqualTo("request-1");
        assertThat(registry.size()).isEqualTo(1);

        registry.clear();
    }

    @Test
    void shouldFindSessionsByRequestId() {
        StreamingSessionRegistry registry = new StreamingSessionRegistry();

        registry.register("request-1");
        registry.register("request-1");
        registry.register("request-2");

        assertThat(registry.findByRequestId("request-1")).hasSize(2);
        assertThat(registry.findByRequestId("request-2")).hasSize(1);

        registry.clear();
    }

    @Test
    void shouldKeepSessionsSeparatedByRequestId() {
        StreamingSessionRegistry registry = new StreamingSessionRegistry();

        StreamingSession first =
                registry.register("request-1");

        StreamingSession second =
                registry.register("request-1");

        StreamingSession unrelated =
                registry.register("request-2");

        assertThat(registry.findByRequestId("request-1"))
                .containsExactlyInAnyOrder(first, second);

        assertThat(registry.findByRequestId("request-1"))
                .doesNotContain(unrelated);

        registry.clear();
    }

    @Test
    void shouldUpdateLastSeenWhenSessionIsTouched() {
        StreamingSessionRegistry registry = new StreamingSessionRegistry();

        StreamingSession session =
                registry.register("request-1");

        Instant beforeTouch = session.lastSeen();

        session.touch();

        assertThat(session.lastSeen())
                .isAfterOrEqualTo(beforeTouch);

        registry.clear();
    }
}

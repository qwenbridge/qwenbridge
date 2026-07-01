package io.qwenbridge.streaming.session;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingSessionRegistryTest {

    @Test
    void shouldRegisterStreamingSession() {
        StreamingSessionRegistry registry = new StreamingSessionRegistry();

        StreamingSession session = registry.register();

        assertThat(session.sessionId()).isNotBlank();
        assertThat(session.emitter()).isNotNull();
        assertThat(session.closed()).isFalse();
        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.find(session.sessionId())).contains(session);
    }

    @Test
    void shouldRemoveStreamingSession() {
        StreamingSessionRegistry registry = new StreamingSessionRegistry();
        StreamingSession session = registry.register();

        boolean removed = registry.remove(session.sessionId());

        assertThat(removed).isTrue();
        assertThat(session.closed()).isTrue();
        assertThat(registry.size()).isZero();
        assertThat(registry.find(session.sessionId())).isEmpty();
    }

    @Test
    void shouldReturnFalseWhenRemovingUnknownSession() {
        StreamingSessionRegistry registry = new StreamingSessionRegistry();

        boolean removed = registry.remove("missing-session");

        assertThat(removed).isFalse();
    }

    @Test
    void shouldClearAllSessions() {
        StreamingSessionRegistry registry = new StreamingSessionRegistry();

        registry.register();
        registry.register();

        registry.clear();

        assertThat(registry.size()).isZero();
    }
}

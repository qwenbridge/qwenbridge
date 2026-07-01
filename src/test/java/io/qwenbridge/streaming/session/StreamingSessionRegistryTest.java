package io.qwenbridge.streaming.session;

import org.junit.jupiter.api.Test;

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
}

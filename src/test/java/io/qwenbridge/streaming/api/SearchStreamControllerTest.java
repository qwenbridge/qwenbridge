package io.qwenbridge.streaming.api;

import io.qwenbridge.streaming.session.StreamingSessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class SearchStreamControllerTest {

    @Test
    void shouldRegisterStreamingSessionForRequestIdAndReturnEmitter() {
        StreamingSessionRegistry registry = new StreamingSessionRegistry();
        SearchStreamController controller = new SearchStreamController(registry);

        SseEmitter emitter = controller.stream("request-1");

        assertThat(emitter).isNotNull();
        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.findByRequestId("request-1")).hasSize(1);

        registry.clear();
    }
}

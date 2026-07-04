package io.qwenbridge.streaming.api;

import io.qwenbridge.operations.metrics.OperationsMetrics;
import io.qwenbridge.streaming.api.validation.StreamRequestIdValidator;
import io.qwenbridge.streaming.config.StreamingProperties;
import io.qwenbridge.streaming.session.StreamingSessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchStreamControllerTest {

    private final StreamingSessionRegistry registry =
            new StreamingSessionRegistry(
                    new StreamingProperties(300_000L, java.time.Duration.ofSeconds(30), 1_000L, 1_100L),
                    mock(OperationsMetrics.class)
            );

    private final SearchStreamController controller =
            new SearchStreamController(
                    registry,
                    new StreamRequestIdValidator()
            );

    @Test
    void shouldRegisterStreamingSessionForValidRequestIdAndReturnEmitter() {
        SseEmitter emitter = controller.stream("client-request-1");

        assertThat(emitter).isNotNull();
        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.findByRequestId("client-request-1")).hasSize(1);

        registry.clear();
    }

    @Test
    void shouldAcceptSafeRequestIdCharacters() {
        SseEmitter emitter =
                controller.stream("request_1:trace.2026-07-03");

        assertThat(emitter).isNotNull();

        registry.clear();
    }

    @Test
    void shouldRejectBlankRequestId() {
        assertThatThrownBy(() -> controller.stream(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("requestId must not be blank");

        assertThat(registry.size()).isZero();
    }

    @Test
    void shouldRejectRequestIdWithUnsupportedCharacters() {
        assertThatThrownBy(() -> controller.stream("../request-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("requestId contains unsupported characters");

        assertThat(registry.size()).isZero();
    }

    @Test
    void shouldRejectRequestIdLongerThanMaximumLength() {
        String requestId = "a".repeat(129);

        assertThatThrownBy(() -> controller.stream(requestId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("requestId must not exceed 128 characters");

        assertThat(registry.size()).isZero();
    }
}

package io.qwenbridge.streaming.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.qwenbridge.operations.metrics.OperationsMetrics;
import io.qwenbridge.streaming.api.validation.StreamRequestIdValidator;
import io.qwenbridge.streaming.config.StreamingProperties;
import io.qwenbridge.streaming.session.StreamingSessionRegistry;
import org.junit.jupiter.api.Test;

class SearchStreamControllerTest {

  private final StreamingSessionRegistry registry =
      new StreamingSessionRegistry(
          new StreamingProperties(300_000L, java.time.Duration.ofSeconds(30), 1_000L, 1_100L),
          mock(OperationsMetrics.class));

  private final StreamRequestIdValidator requestIdValidator = new StreamRequestIdValidator();

  @Test
  void shouldRegisterStreamingSessionForValidRequestId() {
    requestIdValidator.validate("client-request-1");
    var session = registry.register("client-request-1");

    assertThat(session).isNotNull();
    assertThat(registry.size()).isEqualTo(1);
    assertThat(registry.findByRequestId("client-request-1")).hasSize(1);

    registry.clear();
  }

  @Test
  void shouldAcceptSafeRequestIdCharacters() {
    requestIdValidator.validate("request_1:trace.2026-07-03");
    var session = registry.register("request_1:trace.2026-07-03");

    assertThat(session).isNotNull();

    registry.clear();
  }

  @Test
  void shouldRejectBlankRequestId() {
    assertThatThrownBy(() -> requestIdValidator.validate(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("requestId must not be blank");

    assertThat(registry.size()).isZero();
  }

  @Test
  void shouldRejectRequestIdWithUnsupportedCharacters() {
    assertThatThrownBy(() -> requestIdValidator.validate("../request-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("requestId contains unsupported characters");

    assertThat(registry.size()).isZero();
  }

  @Test
  void shouldRejectRequestIdLongerThanMaximumLength() {
    String requestId = "a".repeat(129);

    assertThatThrownBy(() -> requestIdValidator.validate(requestId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("requestId must not exceed 128 characters");

    assertThat(registry.size()).isZero();
  }
}

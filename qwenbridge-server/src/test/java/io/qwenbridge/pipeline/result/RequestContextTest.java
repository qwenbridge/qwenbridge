package io.qwenbridge.pipeline.result;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestContextTest {

  @Test
  void shouldUseClientProvidedRequestId() {
    RequestContext context = RequestContext.of("client-request-1", "table");

    assertThat(context.requestId()).isEqualTo("client-request-1");
    assertThat(context.originalQuery()).isEqualTo("table");
  }

  @Test
  void shouldGenerateRequestIdWhenClientRequestIdIsBlank() {
    RequestContext context = RequestContext.of("", "table");

    assertThat(context.requestId()).isNotBlank();
    assertThat(context.originalQuery()).isEqualTo("table");
  }
}

package io.qwenbridge.streaming.session;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

/** {@link SessionSink} backed by a JAX-RS SSE connection. */
public final class JaxRsSessionSink implements SessionSink {

  private final SseEventSink eventSink;
  private final Sse sse;

  public JaxRsSessionSink(SseEventSink eventSink, Sse sse) {
    this.eventSink = eventSink;
    this.sse = sse;
  }

  @Override
  public void send(String eventId, String eventName, Object payload) {
    if (eventSink.isClosed()) {
      throw new IllegalStateException("SSE sink is closed");
    }

    eventSink.send(
        sse.newEventBuilder()
            .id(eventId)
            .name(eventName)
            .mediaType(MediaType.APPLICATION_JSON_TYPE)
            .data(payload)
            .build());
  }

  @Override
  public void close() {
    if (!eventSink.isClosed()) {
      eventSink.close();
    }
  }

  @Override
  public boolean isClosed() {
    return eventSink.isClosed();
  }
}

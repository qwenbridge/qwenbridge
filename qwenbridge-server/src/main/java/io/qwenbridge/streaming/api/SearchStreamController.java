package io.qwenbridge.streaming.api;

import io.qwenbridge.streaming.api.validation.StreamRequestIdValidator;
import io.qwenbridge.streaming.event.ConnectedStreamingPayload;
import io.qwenbridge.streaming.session.StreamingSession;
import io.qwenbridge.streaming.session.StreamingSessionRegistry;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

@Path("/api/v1/search")
public class SearchStreamController {

  private final StreamingSessionRegistry registry;
  private final StreamRequestIdValidator requestIdValidator;

  public SearchStreamController(
      StreamingSessionRegistry registry, StreamRequestIdValidator requestIdValidator) {
    this.registry = registry;
    this.requestIdValidator = requestIdValidator;
  }

  @GET
  @Path("/stream/{requestId}")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  public void stream(
      @PathParam("requestId") String requestId,
      @Context SseEventSink eventSink,
      @Context Sse sse) {
    requestIdValidator.validate(requestId);

    StreamingSession session = registry.register(requestId, eventSink, sse);

    try {
      session
          .sink()
          .send(
              null,
              "stream.connected",
              new ConnectedStreamingPayload(requestId, session.sessionId()));
    } catch (RuntimeException ex) {
      registry.remove(session.sessionId());

      throw new IllegalStateException(
          "Unable to establish SSE stream for requestId: " + requestId, ex);
    }
  }
}
